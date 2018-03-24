/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.ingest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Creates ingest tasks for data source ingest jobs, queueing the tasks in
 * priority order for execution by the ingest manager's ingest threads.
 */
final class IngestTasksScheduler {

    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();
    private static final Logger logger = Logger.getLogger(IngestTasksScheduler.class.getName());
    private static IngestTasksScheduler instance;
    private final DataSourceIngestTaskQueue dataSourceTaskQueue;
    private final TreeSet<FileIngestTask> rootFileTaskQueue;
    private final Deque<FileIngestTask> directoryFileTaskQueue;
    private final List<FileIngestTask> queuedFileTasks; // RJCTODO: Consider putting this in the queue class
    private final FileIngestTaskQueue fileTaskQueue;

    /**
     * Gets the ingest tasks scheduler singleton.
     */
    synchronized static IngestTasksScheduler getInstance() {
        if (IngestTasksScheduler.instance == null) {
            IngestTasksScheduler.instance = new IngestTasksScheduler();
        }
        return IngestTasksScheduler.instance;
    }

    /**
     * Constructs an ingest tasks scheduler.
     */
    private IngestTasksScheduler() {
        this.dataSourceTaskQueue = new DataSourceIngestTaskQueue();
        this.rootFileTaskQueue = new TreeSet<>(new RootDirectoryTaskComparator());
        this.directoryFileTaskQueue = new LinkedList<>();
        this.queuedFileTasks = new LinkedList<>();
        this.fileTaskQueue = new FileIngestTaskQueue();
    }

    /**
     * Gets the data source level ingest tasks queue. This queue is a blocking
     * queue intended for use by the ingest manager's data source ingest
     * threads.
     *
     * @return The queue.
     */
    IngestTaskQueue getDataSourceIngestTaskQueue() {
        return this.dataSourceTaskQueue;
    }

    /**
     * Gets the file level ingest tasks queue. This queue is a blocking queue
     * intended for use by the ingest manager's file ingest threads.
     *
     * @return The queue.
     */
    IngestTaskQueue getFileIngestTaskQueue() {
        return this.fileTaskQueue;
    }

    /**
     * Schedules a data source level ingest task and file level ingest tasks for
     * a data source ingest job.
     *
     * @param job The data source ingest job.
     */
    synchronized void scheduleIngestTasks(DataSourceIngestJob job) {
        if (!job.isCancelled()) {
            /*
             * Scheduling of both the data source ingest task and the initial
             * file ingest tasks for a job must be an atomic operation.
             * Otherwise, the data source task might be completed before the
             * file tasks are scheduled, resulting in a potential false positive
             * when another thread checks whether or not all the tasks for the
             * job are completed.
             */
            this.scheduleDataSourceIngestTask(job);
            this.scheduleFileIngestTasks(job);
        }
    }

    /**
     * Schedules a data source level ingest task for a data source ingest job.
     *
     * @param job The data source ingest job.
     */
    synchronized void scheduleDataSourceIngestTask(DataSourceIngestJob job) {
        if (!job.isCancelled()) {
            DataSourceIngestTask task = new DataSourceIngestTask(job);
            this.dataSourceTaskQueue.add(task);
        }
    }

    /**
     * Schedules file level ingest tasks for a data source ingest job.
     *
     * @param job The data source ingest job.
     */
    synchronized void scheduleFileIngestTasks(DataSourceIngestJob job) {
        if (!job.isCancelled()) {
            List<AbstractFile> candidateFiles = getTopLevelFiles(job.getDataSource());
            for (AbstractFile file : candidateFiles) {
                FileIngestTask task = new FileIngestTask(job, file);
                if (IngestTasksScheduler.shouldEnqueueFileTask(task)) {
                    this.rootFileTaskQueue.add(task);
                }
            }
            shuffleFileTaskQueues();
        }
    }

    /**
     * Schedules file level ingest tasks for a subset of the files for a data
     * source ingest job.
     *
     * @param job   The data source ingest job.
     * @param files A subset of the files for the data source.
     */
    synchronized void scheduleFileIngestTasks(DataSourceIngestJob job, Collection<AbstractFile> files) {
        if (!job.isCancelled()) {
            List<FileIngestTask> newTasksForFileIngestThreads = new LinkedList<>();
            for (AbstractFile file : files) {
                /*
                 * Put the file directly into the queue for the file ingest
                 * threads, if it passes the filter for the job. The file is
                 * added to the queue for the ingest threads BEFORE the other
                 * queued tasks because the primary use case for this method is
                 * adding derived files from a higher priority task that
                 * preceded the tasks currently in the queue.
                 */
                FileIngestTask task = new FileIngestTask(job, file);
                if (shouldEnqueueFileTask(task)) {
                    newTasksForFileIngestThreads.add(task);
                }

                /*
                 * If the file or directory that was just queued has children,
                 * try to queue tasks for the children. Each child task will go
                 * into either the directory queue if it is a directory, or
                 * directly into the queue for the file ingest threads, if it
                 * passes the filter for the job.
                 */
                try {
                    for (Content child : file.getChildren()) {
                        if (child instanceof AbstractFile) {
                            AbstractFile childFile = (AbstractFile) child;
                            FileIngestTask childTask = new FileIngestTask(job, childFile);
                            if (childFile.hasChildren()) {
                                this.directoryFileTaskQueue.add(childTask);
                            } else if (shouldEnqueueFileTask(childTask)) {
                                newTasksForFileIngestThreads.add(task);
                            }
                        }
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error getting the children of %s (objId=%d)", file.getName(), file.getId()), ex);  //NON-NLS
                }
            }

            /*
             * The files are added to the queue for the ingest threads BEFORE
             * the other queued tasks because the primary use case for this
             * method is adding derived files from a higher priority task that
             * preceded the tasks currently in the queue.
             */
            if (!newTasksForFileIngestThreads.isEmpty()) {
                for (FileIngestTask newTask : newTasksForFileIngestThreads) {
                    try {
                        this.queuedFileTasks.add(newTask);
                        this.fileTaskQueue.addFirst(newTask);
                    } catch (InterruptedException ex) {
                        this.queuedFileTasks.remove(newTask);
                        IngestTasksScheduler.logger.log(Level.INFO, "Ingest cancelled while blocked on a full file ingest threads queue", ex);
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } else {
                /*
                 * Only directory tasks were queued, so shuffle.
                 */
                this.shuffleFileTaskQueues();
            }
        }
    }

    /**
     * Allows an ingest thread to notify this ingest task scheduler that a data
     * source level task has been completed.
     *
     * @param task The completed task.
     */
    synchronized void notifyTaskCompleted(DataSourceIngestTask task) {
        this.dataSourceTaskQueue.taskCompleted(task);
    }

    /**
     * Allows an ingest thread to notify this ingest task scheduler that a file
     * level task has been completed.
     *
     * @param task The completed task.
     */
    synchronized void notifyTaskCompleted(FileIngestTask task) {
        this.queuedFileTasks.remove(task);
        shuffleFileTaskQueues();
    }

    /**
     * Queries the task scheduler to determine whether or not all of the ingest
     * tasks for a data source ingest job have been completed.
     *
     * @param job The data source ingest job.
     *
     * @return True or false.
     */
    synchronized boolean tasksForJobAreCompleted(DataSourceIngestJob job) {
        long jobId = job.getId();
        return !this.dataSourceTaskQueue.hasTasksForJob(jobId)
                && !hasTasksForJob(this.rootFileTaskQueue, jobId)
                && !hasTasksForJob(this.directoryFileTaskQueue, jobId)
                && !hasTasksForJob(this.queuedFileTasks, jobId);
    }

    /**
     * Clears the "upstream" task scheduling queues for a data source ingest
     * job, but does nothing about tasks that have already been moved into the
     * queue that is consumed by the file ingest threads.
     *
     * @param job The data source ingest job.
     */
    synchronized void cancelPendingTasksForIngestJob(DataSourceIngestJob job) {
        long jobId = job.getId();
        IngestTasksScheduler.removeTasksForJob(this.rootFileTaskQueue, jobId);
        IngestTasksScheduler.removeTasksForJob(this.directoryFileTaskQueue, jobId);
    }

    /**
     * Gets the top level files such as file system root directories, layout
     * files and virtual directories for a data source. Used to create file
     * tasks to put into the root directories queue.
     *
     * @param dataSource The data source.
     *
     * @return The top level files.
     */
    private static List<AbstractFile> getTopLevelFiles(Content dataSource) {
        List<AbstractFile> topLevelFiles = new ArrayList<>();
        Collection<AbstractFile> rootObjects = dataSource.accept(new GetRootDirectoryVisitor());
        if (rootObjects.isEmpty() && dataSource instanceof AbstractFile) {
            // The data source is itself a file to be processed.
            topLevelFiles.add((AbstractFile) dataSource);
        } else {
            for (AbstractFile root : rootObjects) {
                List<Content> children;
                try {
                    children = root.getChildren();
                    if (children.isEmpty()) {
                        // Add the root object itself, it could be an unallocated
                        // space file, or a child of a volume or an image.
                        topLevelFiles.add(root);
                    } else {
                        // The root object is a file system root directory, get
                        // the files within it.
                        for (Content child : children) {
                            if (child instanceof AbstractFile) {
                                topLevelFiles.add((AbstractFile) child);
                            }
                        }
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Could not get children of root to enqueue: " + root.getId() + ": " + root.getName(), ex); //NON-NLS
                }
            }
        }
        return topLevelFiles;
    }

    /**
     * Schedules file ingest tasks for the ingest manager's file ingest threads
     * by "shuffling" them through a sequence of three queues that allows for
     * the interleaving of tasks from different data source ingest jobs based on
     * priority. The sequence of queues is:
     *
     * 1. The root file tasks priority queue, which contains file tasks for the
     * root objects of the data sources that are being analyzed. For example,
     * the root tasks for a disk image data source are typically the tasks for
     * the contents of the root directories of the file systems. This queue is a
     * priority queue that attempts to ensure that user directory content is
     * analyzed before general file system content. It feeds into the directory
     * tasks queue.
     *
     * 2. The directory file tasks queue, which contains root file tasks
     * shuffled out of the root tasks queue, plus directory tasks discovered in
     * the descent from the root tasks to the final leaf tasks in the content
     * trees that are being analyzed for the data source ingest jobs. This queue
     * is a FIFO queue. It feeds into the file tasks queue for the ingest
     * manager's file ingest threads.
     *
     * 3. The file tasks queue for the ingest manager's file ingest threads.
     * This queue is a blocking deque that is FIFO during a shuffle to maintain
     * task prioritization, but LIFO when adding derived files to it directly
     * during ingest. The reason for the LIFO additions is to give priority
     * derived files of priority files.
     *
     * There is a fourth collection of file tasks, a "tracking" list, that keeps
     * track of the file tasks that are either in the tasks queue for the file
     * ingest threads, or are in the process of being analyzed in a file ingest
     * thread. This queue is vital to the ingest task scheduler's ability to
     * determine when all of the ingest tasks for a data source ingest job have
     * been completed. It is also used to drive this shuffling algorithm -
     * whenever this list is empty, the two "upstream" queues are "shuffled" to
     * queue more tasks for the file ingest threads.
     */
    synchronized private void shuffleFileTaskQueues() {
        List<FileIngestTask> newTasksForFileIngestThreads = new LinkedList<>();
        while (this.queuedFileTasks.isEmpty()) {
            /*
             * If the directory file task queue is empty, move the highest
             * priority root file task, if there is one, into it. If both the
             * root and the directory file task queues are empty, there is
             * nothing left to shuffle, so exit.
             */
            if (this.directoryFileTaskQueue.isEmpty()) {
                if (!this.rootFileTaskQueue.isEmpty()) {
                    this.directoryFileTaskQueue.add(this.rootFileTaskQueue.pollFirst());
                } else {
                    return;
                }
            }

            /*
             * Try to move the next task from the directory task queue into the
             * queue for the file ingest threads, if it passes the filter for
             * the job. The file is added to the queue for the ingest threads
             * AFTER the higher priority tasks that preceded it.
             */
            final FileIngestTask directoryTask = this.directoryFileTaskQueue.pollLast();
            if (shouldEnqueueFileTask(directoryTask)) {
                newTasksForFileIngestThreads.add(directoryTask);
                this.queuedFileTasks.add(directoryTask);
            }

            /*
             * If the directory (or root level file) that was just queued has
             * children, try to queue tasks for the children. Each child task
             * will go into either the directory queue if it is a directory, or
             * into the queue for the file ingest threads, if it passes the
             * filter for the job. The file is added to the queue for the ingest
             * threads AFTER the higher priority tasks that preceded it.
             */
            final AbstractFile directory = directoryTask.getFile();
            try {
                for (Content child : directory.getChildren()) {
                    if (child instanceof AbstractFile) {
                        AbstractFile childFile = (AbstractFile) child;
                        FileIngestTask childTask = new FileIngestTask(directoryTask.getIngestJob(), childFile);
                        if (childFile.hasChildren()) {
                            this.directoryFileTaskQueue.add(childTask);
                        } else if (shouldEnqueueFileTask(childTask)) {
                            newTasksForFileIngestThreads.add(childTask);
                            this.queuedFileTasks.add(childTask);
                        }
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Error getting the children of %s (objId=%d)", directory.getName(), directory.getId()), ex);  //NON-NLS
            }
        }

        /*
         * The files are added to the queue for the ingest threads AFTER the
         * higher priority tasks that preceded them.
         */
        for (FileIngestTask newTask : newTasksForFileIngestThreads) {
            try {
                this.fileTaskQueue.addFirst(newTask);
            } catch (InterruptedException ex) {
                this.queuedFileTasks.remove(newTask);
                IngestTasksScheduler.logger.log(Level.INFO, "Ingest cancelled while blocked on a full file ingest threads queue", ex);
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Examines the file associated with a file ingest task to determine whether
     * or not the file should be processed and therefore whether or not the task
     * should be enqueued.
     *
     * @param task The task to be scrutinized.
     *
     * @return True or false.
     */
    private static boolean shouldEnqueueFileTask(final FileIngestTask task) {
        final AbstractFile file = task.getFile();

        // Skip the task if the file is actually the pseudo-file for the parent
        // or current directory.
        String fileName = file.getName();

        if (fileName.equals(".") || fileName.equals("..")) {
            return false;
        }

        /*
         * Check if the file is a member of the file ingest filter that is being
         * applied to the current run of ingest, checks if unallocated space
         * should be processed inside call to fileIsMemberOf
         */
        if (file.isFile() && task.getIngestJob().getFileIngestFilter().fileIsMemberOf(file) == null) {
            return false;
        }

        // Skip the task if the file is one of a select group of special, large
        // NTFS or FAT file system files.
        if (file instanceof org.sleuthkit.datamodel.File) {
            final org.sleuthkit.datamodel.File f = (org.sleuthkit.datamodel.File) file;

            // Get the type of the file system, if any, that owns the file.
            TskData.TSK_FS_TYPE_ENUM fsType = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_UNSUPP;
            try {
                FileSystem fs = f.getFileSystem();
                if (fs != null) {
                    fsType = fs.getFsType();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error querying file system for " + f, ex); //NON-NLS
            }

            // If the file system is not NTFS or FAT, don't skip the file.
            if ((fsType.getValue() & FAT_NTFS_FLAGS) == 0) {
                return true;
            }

            // Find out whether the file is in a root directory. 
            boolean isInRootDir = false;
            try {
                AbstractFile parent = f.getParentDirectory();
                isInRootDir = parent.isRoot();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error querying parent directory for" + f.getName(), ex); //NON-NLS
            }

            // If the file is in the root directory of an NTFS or FAT file 
            // system, check its meta-address and check its name for the '$'
            // character and a ':' character (not a default attribute).
            if (isInRootDir && f.getMetaAddr() < 32) {
                String name = f.getName();
                if (name.length() > 0 && name.charAt(0) == '$' && name.contains(":")) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks whether or not a collection of ingest tasks includes a task for a
     * given data source ingest job.
     *
     * @param tasks The tasks.
     * @param jobId The data source ingest job id.
     *
     * @return True if there are no tasks for the job, false otherwise.
     */
    synchronized private static boolean hasTasksForJob(Collection<? extends IngestTask> tasks, long jobId) {
        for (IngestTask task : tasks) {
            if (task.getIngestJob().getId() == jobId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes all of the ingest tasks associated with a data source ingest job
     * from a tasks collection.
     *
     * @param tasks The collection from which to remove the tasks.
     * @param jobId The data source ingest job id.
     */
    private static void removeTasksForJob(Collection<? extends IngestTask> tasks, long jobId) {
        Iterator<? extends IngestTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            IngestTask task = iterator.next();
            if (task.getIngestJob().getId() == jobId) {
                iterator.remove();
            }
        }
    }

    /**
     * Counts the number of ingest tasks in a tasks collection for a given job.
     *
     * @param queue The queue for which to count tasks.
     * @param jobId The id of the job for which the tasks are to be counted.
     *
     * @return The count.
     */
    private static int countTasksForJob(Collection<? extends IngestTask> queue, long jobId) {
        int count = 0;
        for (IngestTask task : queue) {
            if (task.getIngestJob().getId() == jobId) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a snapshot of the states of the tasks in progress for an ingest
     * job.
     *
     * @param jobId The identifier assigned to the job.
     *
     * @return
     */
    synchronized IngestJobTasksSnapshot getTasksSnapshotForJob(long jobId) {
        return new IngestJobTasksSnapshot(jobId);
    }

    /**
     * Prioritizes tasks for the root directories file ingest tasks queue (file
     * system root directories, layout files and virtual directories).
     */
    private static class RootDirectoryTaskComparator implements Comparator<FileIngestTask> {

        @Override
        public int compare(FileIngestTask q1, FileIngestTask q2) {
            AbstractFilePriority.Priority p1 = AbstractFilePriority.getPriority(q1.getFile());
            AbstractFilePriority.Priority p2 = AbstractFilePriority.getPriority(q2.getFile());
            if (p1 == p2) {
                return (int) (q2.getFile().getId() - q1.getFile().getId());
            } else {
                return p2.ordinal() - p1.ordinal();
            }
        }

        /**
         * Used to prioritize file ingest tasks in the root tasks queue so that
         * user content is processed first.
         */
        private static class AbstractFilePriority {

            private AbstractFilePriority() {
            }

            enum Priority {

                LAST, LOW, MEDIUM, HIGH
            }

            static final List<Pattern> LAST_PRI_PATHS = new ArrayList<>();

            static final List<Pattern> LOW_PRI_PATHS = new ArrayList<>();

            static final List<Pattern> MEDIUM_PRI_PATHS = new ArrayList<>();

            static final List<Pattern> HIGH_PRI_PATHS = new ArrayList<>();

            /*
             * prioritize root directory folders based on the assumption that we
             * are looking for user content. Other types of investigations may
             * want different priorities.
             */
            static /*
             * prioritize root directory folders based on the assumption that we
             * are looking for user content. Other types of investigations may
             * want different priorities.
             */ {
                // these files have no structure, so they go last
                //unalloc files are handled as virtual files in getPriority()
                //LAST_PRI_PATHS.schedule(Pattern.compile("^\\$Unalloc", Pattern.CASE_INSENSITIVE));
                //LAST_PRI_PATHS.schedule(Pattern.compile("^\\Unalloc", Pattern.CASE_INSENSITIVE));
                LAST_PRI_PATHS.add(Pattern.compile("^pagefile", Pattern.CASE_INSENSITIVE));
                LAST_PRI_PATHS.add(Pattern.compile("^hiberfil", Pattern.CASE_INSENSITIVE));
                // orphan files are often corrupt and windows does not typically have
                // user content, so put them towards the bottom
                LOW_PRI_PATHS.add(Pattern.compile("^\\$OrphanFiles", Pattern.CASE_INSENSITIVE));
                LOW_PRI_PATHS.add(Pattern.compile("^Windows", Pattern.CASE_INSENSITIVE));
                // all other files go into the medium category too
                MEDIUM_PRI_PATHS.add(Pattern.compile("^Program Files", Pattern.CASE_INSENSITIVE));
                // user content is top priority
                HIGH_PRI_PATHS.add(Pattern.compile("^Users", Pattern.CASE_INSENSITIVE));
                HIGH_PRI_PATHS.add(Pattern.compile("^Documents and Settings", Pattern.CASE_INSENSITIVE));
                HIGH_PRI_PATHS.add(Pattern.compile("^home", Pattern.CASE_INSENSITIVE));
                HIGH_PRI_PATHS.add(Pattern.compile("^ProgramData", Pattern.CASE_INSENSITIVE));
            }

            /**
             * Get the enabled priority for a given file.
             *
             * @param abstractFile
             *
             * @return
             */
            static AbstractFilePriority.Priority getPriority(final AbstractFile abstractFile) {
                if (!abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.FS)) {
                    //quickly filter out unstructured content
                    //non-fs virtual files and dirs, such as representing unalloc space
                    return AbstractFilePriority.Priority.LAST;
                }
                //determine the fs files priority by name
                final String path = abstractFile.getName();
                if (path == null) {
                    return AbstractFilePriority.Priority.MEDIUM;
                }
                for (Pattern p : HIGH_PRI_PATHS) {
                    Matcher m = p.matcher(path);
                    if (m.find()) {
                        return AbstractFilePriority.Priority.HIGH;
                    }
                }
                for (Pattern p : MEDIUM_PRI_PATHS) {
                    Matcher m = p.matcher(path);
                    if (m.find()) {
                        return AbstractFilePriority.Priority.MEDIUM;
                    }
                }
                for (Pattern p : LOW_PRI_PATHS) {
                    Matcher m = p.matcher(path);
                    if (m.find()) {
                        return AbstractFilePriority.Priority.LOW;
                    }
                }
                for (Pattern p : LAST_PRI_PATHS) {
                    Matcher m = p.matcher(path);
                    if (m.find()) {
                        return AbstractFilePriority.Priority.LAST;
                    }
                }
                //default is medium
                return AbstractFilePriority.Priority.MEDIUM;
            }
        }
    }

    /**
     * A blocking queue of data source ingest tasks that keeps track of both
     * queued and running jobs.
     */
    private final class DataSourceIngestTaskQueue implements IngestTaskQueue {

        private final BlockingQueue<DataSourceIngestTask> taskQueue = new LinkedBlockingQueue<>();
        private final List<DataSourceIngestTask> queuedTasks = new LinkedList<>();
        private final List<DataSourceIngestTask> runningTasks = new LinkedList<>();

        private void add(DataSourceIngestTask task) {
            synchronized (IngestTasksScheduler.this) {
                this.queuedTasks.add(task);
            }
            try {
                this.taskQueue.put(task);
            } catch (InterruptedException ex) {
                synchronized (IngestTasksScheduler.this) {
                    this.queuedTasks.remove(task);
                }
                IngestTasksScheduler.logger.log(Level.INFO, "Ingest cancelled while blocked on a full file ingest threads queue", ex);
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public IngestTask getNextTask() throws InterruptedException {
            DataSourceIngestTask task = taskQueue.take();
            synchronized (IngestTasksScheduler.this) {
                this.queuedTasks.remove(task);
                this.runningTasks.add(task);
            }
            return task;
        }

        private void taskCompleted(DataSourceIngestTask task) {
            synchronized (IngestTasksScheduler.this) {
                this.runningTasks.remove(task);
            }
        }

        private boolean hasTasksForJob(long jobId) {
            synchronized (IngestTasksScheduler.this) {
                return IngestTasksScheduler.hasTasksForJob(this.queuedTasks, jobId) || IngestTasksScheduler.hasTasksForJob(this.runningTasks, jobId);
            }
        }

        private int countQueuedTasksForJob(long jobId) {
            synchronized (IngestTasksScheduler.this) {
                return IngestTasksScheduler.countTasksForJob(this.queuedTasks, jobId);
            }
        }

        private int countRunningTasksForJob(long jobId) {
            synchronized (IngestTasksScheduler.this) {
                return IngestTasksScheduler.countTasksForJob(this.runningTasks, jobId);
            }
        }
        
    }

    /**
     * A blocking, LIFO queue of data source ingest tasks for the ingest
     * manager's data source ingest threads.
     */
    private final class FileIngestTaskQueue implements IngestTaskQueue {

        private final BlockingDeque<FileIngestTask> tasks = new LinkedBlockingDeque<>();

        private void addFirst(FileIngestTask task) throws InterruptedException {
            this.tasks.putFirst(task);
        }

        private void addLast(FileIngestTask task) throws InterruptedException {
            this.tasks.putLast(task);
        }

        @Override
        public IngestTask getNextTask() throws InterruptedException {
            return tasks.takeFirst();
        }

    }

    /**
     * A snapshot of ingest tasks data for an ingest job.
     */
    class IngestJobTasksSnapshot {

        private final long jobId;
        private final long dsQueueSize;
        private final long rootQueueSize;
        private final long dirQueueSize;
        private final long fileQueueSize;
        private final long runningListSize;

        /**
         * Constructs a snapshot of ingest tasks data for an ingest job.
         *
         * @param jobId The identifier associated with the job.
         */
        IngestJobTasksSnapshot(long jobId) {
            this.jobId = jobId;
            this.dsQueueSize = IngestTasksScheduler.this.dataSourceTaskQueue.countQueuedTasksForJob(jobId);
            this.rootQueueSize = countTasksForJob(IngestTasksScheduler.this.rootFileTaskQueue, jobId);
            this.dirQueueSize = countTasksForJob(IngestTasksScheduler.this.directoryFileTaskQueue, jobId);
            this.fileQueueSize = countTasksForJob(IngestTasksScheduler.this.fileTaskQueue.tasks, jobId);
            this.runningListSize = IngestTasksScheduler.this.dataSourceTaskQueue.countRunningTasksForJob(jobId) + countTasksForJob(IngestTasksScheduler.this.queuedFileTasks, jobId);
        }

        /**
         * Gets the identifier associated with the ingest job for which this
         * snapshot was created.
         *
         * @return The ingest job identifier.
         */
        long getJobId() {
            return jobId;
        }

        /**
         * Gets the number of file ingest tasks associated with the job that are
         * in the root directories queue.
         *
         * @return The tasks count.
         */
        long getRootQueueSize() {
            return rootQueueSize;
        }

        /**
         * Gets the number of file ingest tasks associated with the job that are
         * in the root directories queue.
         *
         * @return The tasks count.
         */
        long getDirectoryTasksQueueSize() {
            return dirQueueSize;
        }

        long getFileQueueSize() {
            return fileQueueSize;
        }

        long getDsQueueSize() {
            return dsQueueSize;
        }

        long getRunningListSize() {
            return runningListSize;
        }

    }

}

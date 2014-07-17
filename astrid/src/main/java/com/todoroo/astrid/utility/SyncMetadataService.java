/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.utility;

import android.content.ContentValues;

import com.todoroo.andlib.data.Callback;
import com.todoroo.andlib.sql.Query;
import com.todoroo.astrid.dao.MetadataDao;
import com.todoroo.astrid.dao.MetadataDao.MetadataCriteria;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.sync.SyncContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

abstract public class SyncMetadataService<TYPE extends SyncContainer> {

    protected final TaskDao taskDao;
    protected final MetadataDao metadataDao;

    // --- abstract methods

    /** @return metadata key identifying this sync provider's metadata */
    abstract public String getMetadataKey();

    // --- implementation

    public SyncMetadataService(TaskDao taskDao, MetadataDao metadataDao) {
        this.taskDao = taskDao;
        this.metadataDao = metadataDao;
    }

    /**
     * Clears metadata information. Used when user logs out of sync provider
     */
    public void clearMetadata() {
        metadataDao.deleteWhere(Metadata.KEY.eq(getMetadataKey()));
    }

    /**
     * Saves a task and its metadata
     */
    public void saveTaskAndMetadata(TYPE task) {
        task.prepareForSaving();
        taskDao.save(task.task);
        synchronizeMetadata(task.task.getId(), task.metadata, getMetadataKey());
    }

    /**
     * Reads metadata out of a task
     * @return null if no metadata found
     */
    public Metadata getTaskMetadata(long taskId) {
        return metadataDao.getFirst(Query.select(Metadata.PROPERTIES).where(
                MetadataCriteria.byTaskAndwithKey(taskId, getMetadataKey())));
    }

    /**
     * Synchronize metadata for given task id. Deletes rows in database that
     * are not identical to those in the metadata list, creates rows that
     * have no match.
     *
     * @param taskId id of task to perform synchronization on
     * @param metadata list of new metadata items to save
     * @param metadataKey metadata key
     */
    private void synchronizeMetadata(long taskId, ArrayList<Metadata> metadata, String metadataKey) {
        final Set<ContentValues> newMetadataValues = new HashSet<>();
        for(Metadata metadatum : metadata) {
            metadatum.setTask(taskId);
            metadatum.clearValue(Metadata.ID);
            newMetadataValues.add(metadatum.getMergedValues());
        }

        metadataDao.byTaskAndKey(taskId, metadataKey, new Callback<Metadata>() {
            @Override
            public void apply(Metadata item) {
                long id = item.getId();

                // clear item id when matching with incoming values
                item.clearValue(Metadata.ID);
                ContentValues itemMergedValues = item.getMergedValues();
                if(newMetadataValues.contains(itemMergedValues)) {
                    newMetadataValues.remove(itemMergedValues);
                } else {
                    // not matched. cut it
                    metadataDao.delete(id);
                }
            }
        });

        // everything that remains shall be written
        for(ContentValues values : newMetadataValues) {
            Metadata item = new Metadata();
            item.mergeWith(values);
            metadataDao.persist(item);
        }
    }
}

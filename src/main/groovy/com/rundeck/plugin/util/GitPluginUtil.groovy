package com.rundeck.plugin.util

import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants
import com.dtolabs.rundeck.core.storage.ResourceMeta
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.core.execution.ExecutionContextImpl
import com.dtolabs.rundeck.core.storage.keys.KeyStorageTree;
import com.dtolabs.rundeck.core.execution.ExecutionListener
import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets

/**
 * Created by luistoledo on 12/18/17.
 */
@CompileStatic
class GitPluginUtil {
    static Map<String, Object> getRenderOpt(String value, boolean secondary, boolean password = false, boolean storagePassword = false, boolean storageKey = false) {
        Map<String, Object> ret = new HashMap<>();
        ret.put(StringRenderingConstants.GROUP_NAME,value);
        if(secondary){
            ret.put(StringRenderingConstants.GROUPING,"secondary");
        }
        if(password){
            ret.put("displayType",StringRenderingConstants.DisplayType.PASSWORD)
        }
        if(storagePassword){
            ret.put(StringRenderingConstants.SELECTION_ACCESSOR_KEY,StringRenderingConstants.SelectionAccessor.STORAGE_PATH)
            ret.put(StringRenderingConstants.STORAGE_PATH_ROOT_KEY,"keys")
            ret.put(StringRenderingConstants.STORAGE_FILE_META_FILTER_KEY, "Rundeck-data-type=password")
        }
        if(storageKey){
            ret.put(StringRenderingConstants.SELECTION_ACCESSOR_KEY,StringRenderingConstants.SelectionAccessor.STORAGE_PATH)
            ret.put(StringRenderingConstants.STORAGE_PATH_ROOT_KEY,"keys")
            ret.put(StringRenderingConstants.STORAGE_FILE_META_FILTER_KEY, "Rundeck-key-type=private")
        }

        return ret;
    }

    /**
     * Reads the contents of a ResourceMeta and returns it as a String.
     * 
     * @param contents the ResourceMeta to read
     * @return the contents as a UTF-8 String
     * @throws IOException if an error occurs reading the contents
     */
    private static String readResourceMetaAsString(ResourceMeta contents) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            contents.writeContent(byteArrayOutputStream);
            return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            byteArrayOutputStream.close();
        }
    }

    /**
     * Retrieves the contents of a resource from the key storage using the provided path and plugin step context.
     * 
     * @param path    the path to the resource in the key storage
     * @param context the Rundeck plugin step context
     * @return the contents of the resource as a String
     * @throws Exception if the resource cannot be found or an error occurs reading the contents
     */
    static String getFromKeyStorage(String path, PluginStepContext context){
        ResourceMeta contents = context.getExecutionContext().getStorageTree().getResource(path).getContents();
        return readResourceMetaAsString(contents);
    }

    /**
     * Retrieves the contents of a resource from the key storage using the provided path and execution context.
     * <p>
     * If the storage tree is available, this method attempts to read the resource at the given path and
     * returns its contents as a String. If the storage tree is null or an error occurs, it logs a message and returns null.
     *
     * @param path    the path to the resource in the key storage
     * @param context the Rundeck execution context
     * @return the contents of the resource as a String, or null if the storage tree is null or an error occurs
     */
    static String getFromKeyStorage(String path, ExecutionContextImpl context){
        KeyStorageTree storageTree = (KeyStorageTree)context.getStorageTree()

        if (storageTree == null){
            ExecutionListener logger = context.getExecutionListener()
            if (logger != null) {
                logger.log(1, "storageTree is null. Cannot retrieve credential from Key Storage.");
            }
            return null
        }

        try {
            ResourceMeta contents = storageTree.getResource(path).getContents();
            return readResourceMetaAsString(contents);
        } catch (Exception e) {
            ExecutionListener logger = context.getExecutionListener()
            if (logger != null) {
                logger.log(1, "Failed to retrieve credential from Key Storage at path '${path}': ${e.message}");
            }
            return null
        }
    }
}

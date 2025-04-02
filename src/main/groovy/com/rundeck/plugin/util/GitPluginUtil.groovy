package com.rundeck.plugin.util

import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants
import com.dtolabs.rundeck.core.storage.ResourceMeta
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.core.execution.ExecutionContextImpl
import com.dtolabs.rundeck.core.storage.keys.KeyStorageTree;
import com.dtolabs.rundeck.core.execution.ExecutionListener
import groovy.transform.CompileStatic

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

    static String getFromKeyStorage(String path, PluginStepContext context){
        ResourceMeta contents = context.getExecutionContext().getStorageTree().getResource(path).getContents();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        contents.writeContent(byteArrayOutputStream);
        String password = new String(byteArrayOutputStream.toByteArray());

        return password;

    }

     static String getFromKeyStorage(String path, ExecutionContextImpl context){
        KeyStorageTree storageTree = (KeyStorageTree)context.getStorageTree()

        if (storageTree!=null){
            ResourceMeta contents = context.getStorageTree().getResource(path).getContents();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            contents.writeContent(byteArrayOutputStream);
            String password = new String(byteArrayOutputStream.toByteArray());

            return password;
        } else {
            ExecutionListener logger = context.getExecutionListener()
            logger.log(1, "storageTree is null. Cannot retrieve password");
            return null
        }

    }
}

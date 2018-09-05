package com.rundeck.plugin.util

import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants
import com.dtolabs.rundeck.core.storage.ResourceMeta
import com.dtolabs.rundeck.plugins.step.PluginStepContext

/**
 * Created by luistoledo on 12/18/17.
 */
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

}

package com.rundeck.plugin.util

import com.dtolabs.rundeck.core.dispatcher.DataContextUtils
import com.dtolabs.rundeck.core.execution.proxy.DefaultSecretBundle
import com.dtolabs.rundeck.core.execution.proxy.SecretBundle
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants
import com.dtolabs.rundeck.core.storage.ResourceMeta
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.core.storage.keys.KeyStorageTree
import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by luistoledo on 12/18/17.
 */
@CompileStatic
class GitPluginUtil {
    private static final Logger logger = LoggerFactory.getLogger(GitPluginUtil.class)
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
    static String getFromKeyStorage(String path, ExecutionContext context){
        KeyStorageTree storageTree = (KeyStorageTree)context.getStorageTree()

        if (storageTree == null){
            logger.warn("storageTree is null. Cannot retrieve credential from Key Storage at path '{}'.", path)
            return null
        }

        try {
            ResourceMeta contents = storageTree.getResource(path).getContents();
            String result = readResourceMetaAsString(contents);
            logger.debug("Successfully retrieved credential from Key Storage at path '{}' ({} chars)", path, result?.length())
            return result
        } catch (Exception e) {
            logger.warn("Failed to retrieve credential from Key Storage at path '{}': {}", path, e.message)
            return null
        }
    }

    /**
     * Extracts the repository name from a Git URL by taking the last path segment
     * and stripping the {@code .git} suffix if present.
     * <p>Handles standard URL formats as well as SCP-style notation
     * ({@code git@host:user/repo.git}).</p>
     *
     * @param gitUrl the Git remote URL
     * @return the repository name, or {@code null} if it cannot be determined
     */
    static String extractRepoName(String gitUrl) {
        if (!gitUrl) return null
        String cleaned = gitUrl.replaceAll('/+$', '')
        int lastSlash = cleaned.lastIndexOf('/')
        int lastColon = cleaned.lastIndexOf(':')
        int lastSep = Math.max(lastSlash, lastColon)
        String name = lastSep >= 0 ? cleaned.substring(lastSep + 1) : cleaned
        if (name.endsWith('.git')) {
            name = name.substring(0, name.length() - 4)
        }
        if (!name || name == '.' || name == '..' || name.contains('/') || name.contains('\\')) {
            return null
        }
        return name
    }

    /**
     * Collects key storage paths from the step configuration for the given property keys.
     * Resolves data references (e.g. ${option.path}) using the execution context before
     * extracting paths. Used by ProxyRunnerPlugin/ProxySecretBundleCreator to tell the
     * Rundeck server which secrets a runner needs.
     *
     * @param context       the Rundeck execution context
     * @param configuration the step configuration map
     * @param storageKeys   configuration property names that hold key storage paths
     * @return list of resolved key storage paths (never null)
     */
    static List<String> listSecretsPathForStep(ExecutionContext context, Map<String, Object> configuration, String... storageKeys) {
        Map<String, Object> resolved = DataContextUtils.replaceDataReferences(
                (Map<String, Object>) configuration,
                context.getDataContext()
        )
        List<String> paths = []
        for (String key : storageKeys) {
            def value = resolved.get(key)
            if (value) {
                paths.add((String) value)
            }
        }
        return paths
    }

    /**
     * Creates a SecretBundle containing the secrets at the key storage paths found in the
     * step configuration. The server calls this before dispatching work to a Rundeck Runner
     * so the runner's AuthorizedSecretStorage can authorize access to these paths.
     *
     * @param context       the Rundeck execution context (server-side, with full storage access)
     * @param configuration the step configuration map
     * @param storageKeys   configuration property names that hold key storage paths
     * @return a SecretBundle with the secret values keyed by their storage paths
     */
    static SecretBundle prepareSecretBundleForStep(ExecutionContext context, Map<String, Object> configuration, String... storageKeys) {
        List<String> paths = listSecretsPathForStep(context, configuration, storageKeys)
        DefaultSecretBundle bundle = new DefaultSecretBundle()
        for (String path : paths) {
            try {
                def resource = context.getStorageTree().getResource(path)
                if (resource != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream()
                    resource.getContents().writeContent(baos)
                    bundle.addSecret(path, baos.toByteArray())
                }
            } catch (Exception e) {
                logger.error("Failed to prepare secret bundle for key path '{}': {}", path, e.message, e)
            }
        }
        return bundle
    }
}

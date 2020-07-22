package com.rundeck.plugin

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.common.INodeSet
import com.dtolabs.rundeck.core.resources.ResourceModelSource
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException
import com.dtolabs.rundeck.core.resources.SourceType
import com.dtolabs.rundeck.core.resources.WriteableModelSource
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParser
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParserException
import com.dtolabs.rundeck.core.resources.format.UnsupportedFormatException
import com.dtolabs.utils.Streams


/**
 * Created by luistoledo on 12/18/17.
 */
class GitResourceModel implements ResourceModelSource , WriteableModelSource{

    private Properties configuration;
    private Framework framework;
    private boolean writable=false;

    String extension
    String fileName
    String localPath

    GitManager gitManager

    void setWritable(){
        this.writable=true;
    }


    GitResourceModel(Properties configuration, Framework framework) {
        this.configuration = configuration
        this.framework = framework

        this.extension=configuration.getProperty(GitResourceModelFactory.GIT_FORMAT_FILE)
        this.writable=Boolean.valueOf(configuration.getProperty(GitResourceModelFactory.WRITABLE))
        this.fileName=configuration.getProperty(GitResourceModelFactory.GIT_FILE)
        this.localPath=configuration.getProperty(GitResourceModelFactory.GIT_BASE_DIRECTORY)

        if(gitManager==null){
            gitManager = new GitManager(configuration)
        }

        if(configuration.getProperty(GitResourceModelFactory.GIT_PASSWORD_STORAGE)) {
            gitManager.setGitPassword(configuration.getProperty(GitResourceModelFactory.GIT_PASSWORD_STORAGE))
        }

        if(configuration.getProperty(GitResourceModelFactory.GIT_KEY_STORAGE)) {
            gitManager.setSshPrivateKeyPath(configuration.getProperty(GitResourceModelFactory.GIT_KEY_STORAGE))
        }

    }

    @Override
    INodeSet getNodes() throws ResourceModelSourceException {

        try {
            InputStream remoteFile = gitManager.getFile(this.localPath);

            final ResourceFormatParser parser;
            try {
                parser = getResourceFormatParser();
            } catch (UnsupportedFormatException e) {
                throw new ResourceModelSourceException(
                        "Response content type is not supported: " + extension, e);
            }
            try {
                return parser.parseDocument(remoteFile);
            } catch (ResourceFormatParserException e) {
                throw new ResourceModelSourceException(
                        "Error requesting Resource Model Source from GIT, "
                                + "Content could not be parsed: "+e.getMessage(),e);
            }

        } catch (ResourceFormatParserException e) {
            throw new ResourceModelSourceException(
                    "Error requesting Resource Model Source from GIT, " +e.getMessage(),e);
        }
        return null
    }

    private ResourceFormatParser getResourceFormatParser() throws UnsupportedFormatException {
        return framework.getResourceFormatParserService().getParserForMIMEType(getMimeType());
    }

    private String getMimeType(){
        if(extension.equalsIgnoreCase("yaml")){
            return "text/yaml";
        }
        if(extension.equalsIgnoreCase("json")){
            return "application/json";
        }
        return "application/xml";
    }

    @Override
    public SourceType getSourceType() {
        return writable ? SourceType.READ_WRITE : SourceType.READ_ONLY;
    }

    @Override
    public WriteableModelSource getWriteable() {
        return writable ? this : null;
    }


    @Override
    public String getSyntaxMimeType() {
        try {
            return getResourceFormatParser().getPreferredMimeType();
        } catch (UnsupportedFormatException e) {
            e.printStackTrace()
        }
        return null
    }

    @Override
    long readData(OutputStream sink) throws IOException, ResourceModelSourceException {
        if (!hasData()) {
            return 0;
        }

        InputStream inputStream = gitManager.getFile(this.localPath)

        return Streams.copyStream(inputStream, sink)
    }

    @Override
    boolean hasData() {
        try{
            gitManager.getFile(this.localPath);
        }catch (Exception e){
            return false;
        }
        return true;
    }

    @Override
    public long writeData(InputStream data) throws IOException, ResourceModelSourceException {
        if (!writable) {
            throw new IllegalArgumentException("Cannot write to file, it is not configured to be writeable");
        }
        File newFile = isToFile(data);
        try {
            getResourceFormatParser().parseDocument(newFile);
        } catch (ResourceFormatParserException e) {
            throw new ResourceModelSourceException(e);
        }

        gitManager.gitCommitAndPush()

        return newFile.length();
    }



    private File isToFile(InputStream is) throws IOException, ResourceModelSourceException {
        try {
            File newFile = new File(localPath+"/"+fileName)

            FileOutputStream fos = new FileOutputStream(newFile)
            Streams.copyStream(is, fos);
            return newFile;
        } catch (UnsupportedFormatException e) {
            throw new ResourceModelSourceException(
                    "Response content type is not supported: " + extension, e);
        }
    }

    @Override
    public String getSourceDescription() {
        String gitURL=configuration.getProperty(GitResourceModelFactory.GIT_URL)
        return "Git repo: "+gitURL+", file:"+this.fileName;
    }
}

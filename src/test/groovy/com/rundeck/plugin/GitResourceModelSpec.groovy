package com.rundeck.plugin

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.common.INodeSet
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParser
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParserService
import com.dtolabs.rundeck.core.storage.keys.KeyStorageTree
import org.rundeck.app.spi.Services
import org.rundeck.storage.api.Resource
import spock.lang.Specification
import com.dtolabs.rundeck.core.storage.ResourceMeta

/**
 * Created by luistoledo on 12/22/17.
 */
class GitResourceModelSpec  extends Specification{

    def "retrieve resource success"() {
        given:

        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)

        File folder = new File(path)
        if(!folder.exists()){
            folder.mkdir()
        }

        String localPath= path + "/" + fileName

        Properties configuration = [gitBaseDirectory:path,gitFormatFile:format,gitFile:fileName]

        def gitManager = Mock(GitManager)
        def resource = new GitResourceModel(configuration,framework)
        resource.setGitManager(gitManager)

        def inputStream = GroovyMock(InputStream)

        when:
        def result = resource.getNodes()

        then:
        1 * gitManager.getFile(path) >> inputStream
        result == nodeSet

        where:
        path        | fileName           | format
        'resources' |'resources.xml'     | 'xml'
        'resources' |'resources.yaml'    | 'yaml'
        'resources' |'resources.json'    | 'json'


    }


    def "catch UnsupportedFormatException"(){
        given:

        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)

        File folder = new File(path)
        if(!folder.exists()){
            folder.mkdir()
        }
        String localPath= path + "/" + fileName

        def file = new File(localPath)
        file.withWriter('UTF-8') { writer ->
            writer.write('Somo wrong format.')
        }

        file.deleteOnExit()

        Properties configuration = [gitBaseDirectory:path,gitFormatFile:format,gitFile:fileName]

        def gitManager = Mock(GitManager)
        def resource = new GitResourceModel(configuration,framework)
        resource.setGitManager(gitManager)


        def inputStream = file.newDataInputStream()

        when:
        def result = resource.getNodes()

        then:
        1 * gitManager.getFile(path) >> inputStream
        result == nodeSet

        where:
        path        | fileName           | format
        'resources' |'resources.xml'     | 'xml'
        'resources' |'resources.yaml'    | 'yaml'
        'resources' |'resources.json'    | 'json'


    }


    def "write data remote resource"(){
        given:

        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)

        File folder = new File(path)
        if(!folder.exists()){
            folder.mkdir()
        }

        def is = new ByteArrayInputStream("a".getBytes())
        Properties configuration = [gitBaseDirectory:path,gitFormatFile:format,gitFile:fileName]

        def gitManager = Mock(GitManager)
        def resource = new GitResourceModel(configuration,framework)
        resource.setGitManager(gitManager)

        resource.setWritable()

        when:
        def result = resource.writeData(is)

        then:
        1 * gitManager.gitCommitAndPush()
        result == 1

        where:
        path        | fileName           | format
        'resources' |'resources.xml'     | 'xml'
        'resources' |'resources.yaml'    | 'yaml'
        'resources' |'resources.json'    | 'json'
    }

    def "has data"(){
        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)

        File folder = new File(path)
        if(!folder.exists()){
            folder.mkdir()
        }

        def is = new ByteArrayInputStream("a".getBytes())
        Properties configuration = [gitBaseDirectory:path,gitFormatFile:format,gitFile:fileName]

        def gitManager = Mock(GitManager)
        def resource = new GitResourceModel(configuration,framework)
        resource.setGitManager(gitManager)

        def inputStream = GroovyMock(InputStream)

        when:
        def result = resource.hasData()

        then:
        1 * gitManager.getFile(path) >> inputStream
        result

        where:
        path        | fileName           | format
        'resources' |'resources.xml'     | 'xml'
        'resources' |'resources.yaml'    | 'yaml'
        'resources' |'resources.json'    | 'json'
    }

    def "retrieve resource success using password authentication from key storage"() {
        given:

        def nodeSet = Mock(INodeSet)
        def framework = getFramework(nodeSet)



        String path = "resources"
        String fileName = "resources.xml"
        String format = "xml"

        File folder = new File(path)
        if(!folder.exists()){
            folder.mkdir()
        }

        Properties configuration = [
                gitBaseDirectory:path,
                gitFormatFile:format,
                gitFile:fileName,
                gitPasswordPathStorage:"gitPassword",
        ]

        def gitManager = Mock(GitManager)

        def inputStream = GroovyMock(InputStream)
        KeyStorageTree keyStorageTree = Mock(KeyStorageTree){
            1 * getResource(_) >> Mock(Resource) {
                1* getContents() >> Mock(ResourceMeta) {
                    writeContent(_) >> { args ->
                        args[0].write('password'.bytes)
                        return 6L
                    }
                }
            }
        }

        Services services = Mock(Services){
            1 * getService(KeyStorageTree) >> keyStorageTree
        }

        when:

        def resource = new GitResourceModel(services,configuration,framework)
        resource.setGitManager(gitManager)

        def result = resource.getNodes()

        then:
        1 * gitManager.getFile(path) >> inputStream
        result == nodeSet



    }


    private Framework getFramework(INodeSet nodeSet){
        def resourceFormatParser = Mock(ResourceFormatParser){
            parseDocument(_) >> nodeSet
        }
        def resourceFormatParserService = Mock(ResourceFormatParserService){
            getParserForMIMEType(_) >> resourceFormatParser
        }
        def framework = Mock(Framework){
            getResourceFormatParserService()>> resourceFormatParserService
        }
        return framework
    }
}

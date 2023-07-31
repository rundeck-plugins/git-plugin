package com.rundeck.plugin

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.common.INodeSet
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParser
import com.dtolabs.rundeck.core.resources.format.ResourceFormatParserService
import spock.lang.Specification

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

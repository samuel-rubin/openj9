/*******************************************************************************
 * Copyright (c) 2019, 2019 IBM Corp. and others
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] http://openjdk.java.net/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 OR LicenseRef-GPL-2.0 WITH Assembly-exception
*******************************************************************************/
import java.util.Date

/**
* This script deletes old artifacts off the Artifactory server 
* 
* Parameters: 
*   JOB_TYPE: Choice
*             Expected values: one of the following
*             Time,                // This tells the script to delete all artifacts that are older that were created over x amount of days ago
*             Number of Builds     // This tells the script to keep x amount of artifacts from a specific project
* JOB_TO_CHECK: String – The folder to keep x amount of builds in 
**/

timestamps {
    node('worker') {
        checkout scm
        def variableFile = load 'buildenv/jenkins/common/variables-functions'
        variableFile.parse_variables_file()
        variableFile.set_artifactory_config()

        def artifactory_server = env.ARTIFACTORY_SERVER + '/artifactory'
        if (params.JOB_TYPE == 'Time'){
            cleanupTime(artifactory_server)
        } else if (params.JOB_TYPE == 'Number of Builds'){
            if (params.JOB_TO_CHECK){
                cleanupBuilds(artifactory_server, params.JOB_TO_CHECK)
            } else {
                error 'Please input a job to cleanup'
            }
        }
    }
}

def cleanupBuilds(artifactory_server, upstreamJobName){
    def jobToCheck = upstreamJobName
    def ARTIFACTORY_NUM_ARTIFACTS = env.ARTIFACTORY_NUM_ARTIFACTS as Integer 

    stage('Discover Stored Artifacts'){
        echo "Cleaning up ${jobToCheck}"
        echo "Keeping the latest ${ARTIFACTORY_NUM_ARTIFACTS} builds"

        def request = httpRequest authentication: env.ARTIFACTORY_CREDS, consoleLogResponseBody: true, url: "${artifactory_server}/api/storage/${env.ARTIFACTORY_REPO}/${jobToCheck}"

        data = readJSON text: request.getContent()
        numberOfArtifacts = data.children.size()
        echo "There are ${numberOfArtifacts} builds"
    } 
    stage('Delete Old Artifacts'){
        if (numberOfArtifacts > ARTIFACTORY_NUM_ARTIFACTS){
            def folderNames = getFolderNumbers(data.children.uri)

            for(i=0; i  < (numberOfArtifacts - ARTIFACTORY_NUM_ARTIFACTS); i++){
                echo "Deleting Build #${folderNames[i]}"
                httpRequest authentication: env.ARTIFACTORY_CREDS, httpMode: 'DELETE', consoleLogResponseBody: true, url: "${artifactory_server}/${env.ARTIFACTORY_REPO}/${jobToCheck}/${folderNames[i]}"
            }
        } else {
            echo 'There are no artifacts to delete'
        } 
    }
}

def getFolderNumbers(someVariable){
    def folderNumbers = []
    someVariable.each{
        folderNumbers.add(it.minus('/') as int )
    }
    return folderNumbers.sort()
}

def cleanupTime(artifactory_server){
    stage('Discover Old Artifacts') {
        def date = new Date()
        ARTIFACTORY_DAYS_TO_KEEP_ARTIFACTS = env.ARTIFACTORY_DAYS_TO_KEEP_ARTIFACTS as Integer
        echo "Getting all artifacts over ${ARTIFACTORY_DAYS_TO_KEEP_ARTIFACTS} days old"
        def request = httpRequest authentication: env.ARTIFACTORY_CREDS, consoleLogResponseBody: true, validResponseCodes: '200,404', url: "${artifactory_server}/api/search/usage?notUsedSince=${date.getTime()}&createdBefore=${date.minus(ARTIFACTORY_DAYS_TO_KEEP_ARTIFACTS).getTime()}&repos=${env.ARTIFACTORY_REPO}"
        data = readJSON text: request.getContent()
        requestStatus = request.getStatus()
    }
    stage ('Delete Old Artifacts'){
        if (requestStatus == 200){
            echo "There are ${data.results.size()} artifacts over ${ARTIFACTORY_DAYS_TO_KEEP_ARTIFACTS} days old.\nCleaning them up"

            def artifacts_to_be_deleted = data.results.uri
            artifacts_to_be_deleted.each() { uri -> 
                httpRequest authentication: env.ARTIFACTORY_CREDS, httpMode: 'DELETE', consoleLogResponseBody: true, url: uri.minus('/api/storage')
            }
            echo 'Deleted all the old artifacts'
        } else if (requestStatus == 404){
            if (data.errors.message.contains("No results found.")){
                echo 'There are no artifacts to delete'
            }else {
                error 'HTTP 404 Not Found. Please check the logs'
            }
        } else {
            error 'Something went terribly wrong. Please check the logs'
        }
    }
}        
 
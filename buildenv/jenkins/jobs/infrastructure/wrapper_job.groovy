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

timeout(time: 6, unit: 'HOURS'){
    timestamps {
        label = (params.LABEL ? params.LABEL : 'worker')
        node (label){
            checkout scm
            
            // This yaml file contains the specifications for the pipeline that will be created
            def VARIABLES = readYaml file: 'buildenv/jenkins/jobs/infrastructure/wrapper_variables.yml'
            def general = VARIABLES.get('general')
            println params
            // The parameters should be a boolean. This will cycle through all of the parameters
            params.each { param ->
                // If the boolean parameter is true, it will create the specified wrapper job
                println param
                if (param.value == true){
                    def specifications = VARIABLES.get(param.key)
                    if (specifications != null){
                        if (specifications.triggers && specifications.triggers.pull_request_builder){
                            specifications.triggers.pull_request_builder.admin_list = getAdminList(specifications.triggers.pull_request_builder.admin_list)
                        }
                        createWrapper(general, specifications)
                    } else {
                        echo "ERROR: ${param.key} is not specified in the variable function"
                    }
                }
            }
        }
    }
}

def createWrapper(GENERAL_SPECIFICATIONS, SPECIFICATIONS){
    stage("Build ${SPECIFICATIONS.job_name}"){
        def parameters = [:]

        parameters = GENERAL_SPECIFICATIONS + SPECIFICATIONS
        
        jobDsl targets: 'buildenv/jenkins/jobs/infrastructure/wrapper_template', ignoreExisting: false, additionalParameters: parameters
    }
}

def getAdminList(admin_list_spec){
    def admin_list = []
    def all_admin_lists = readYaml file: 'buildenv/jenkins/variables/admin_list.yml'

    switch(admin_list_spec) {
        case 'OpenJDK':
            admin_list.add(all_admin_lists.extended)
        case 'OpenJ9':
            admin_list.add(all_admin_lists.committers)
    }
    println admin_list
    return admin_list
}

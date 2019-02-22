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
        node ('master || worker'){
            checkout scm
            
            // This yaml file contains the specifications for the pipeline that will be created
            def VARIABLES = readYaml file: 'buildenv/jenkins/jobs/infrastructure/wrapper_variables.yml'
            def general = VARIABLES.get('general')
            
            // The parameters should be a boolean. This will cycle through all of the parameters
            params.each { param ->
                // If the boolean parameter is true, it will create the specified wrapper job
                if (param){
                    def specifications = VARIABLES.get(param.key)
                    if (specifications != null){
                        createWrapper(general, specifications)
                    }
                }
            }
        }
    }
}

def createWrapper(GENERAL_SPECIFICATIONS, SPECIFICATIONS){
    stage("Build ${SPECIFICATIONS.job_name}"){
        def parameters = [:]

        // This will go through all of the parameters and add them to the template
        GENERAL_SPECIFICATIONS.each { general_specification ->
            parameters.put(general_specification.key, general_specification.value)
        }
        SPECIFICATIONS.each { specification ->
            parameters.put(specification.key, specification.value)
        }
        
        jobDsl targets: 'buildenv/jenkins/jobs/infrastructure/wrapper_template', ignoreExisting: false, additionalParameters: parameters
    }

}

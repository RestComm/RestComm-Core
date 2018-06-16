
def runTestsuite(exludedGroups = "org.restcomm.connect.commons.annotations.BrokenTests",groups = "", forkCount=1, profile="defaultProfile") {
        sh "mvn -f restcomm/restcomm.testsuite/pom.xml  install -DskipUTs=false  -Dmaven.test.failure.ignore=true -Dmaven.test.redirectTestOutputToFile=true -Dfailsafe.rerunFailingTestsCount=1 -P $profile -DforkCount=\"$forkCount\" -Dgroups=\"$groups\" -DexcludedGroups=\"$exludedGroups\""
}


def buildRC() {
        // Run the maven build with in-module unit testing and sonar
        try {
            if (env.BRANCH_NAME == 'master') {
                //do sonar just in master
                sh "mvn -f restcomm/pom.xml -pl \\!restcomm.testsuite -Dmaven.test.redirectTestOutputToFile=true -Dsonar.host.url=https://sonarqube.com -Dsonar.login=dd43f79a4bd32b1f2c484362e8a4de676a8388c4 -Dsonar.organization=jaimecasero-github -Dsonar.branch=master clean install sonar:sonar"
            } else {
                sh "mvn -f restcomm/pom.xml -pl \\!restcomm.testsuite -Dmaven.test.redirectTestOutputToFile=true clean install"
            }
        } catch(err) {
            publishRCResults()
            throw err
        }
}

def publishRCResults() {
    junit testResults: '**/target/surefire-reports/*.xml', testDataPublishers: [[$class: 'StabilityTestDataPublisher']]
    checkstyle canComputeNew: false, defaultEncoding: '', healthy: '', pattern: '**/checkstyle-result.xml', unHealthy: ''
    step( [ $class: 'JacocoPublisher' ] )
    if ((env.BRANCH_NAME == 'master') && (currentBuild.currentResult != 'SUCCESS') ) {
        slackSend "Build unstable - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
    }
}

node("cxs-ups-testsuites_large") {

    echo sh(returnStdout: true, script: 'env')

    configFileProvider(
        [configFile(fileId: '37cb206e-6498-4d8a-9b3d-379cd0ccd99b',  targetLocation: 'settings.xml')]) {
	    sh 'mkdir -p ~/.m2 && sed -i "s|@LOCAL_REPO_PATH@|$WORKSPACE/M2_REPO|g" $WORKSPACE/settings.xml && cp $WORKSPACE/settings.xml -f ~/.m2/settings.xml'
    }    
    
    stage ('Checkout') {
        checkout scm
    }

    stage ("Build") {
        buildRC()
    }

    stage("CITestsuiteSeq") {
            runTestsuite("org.restcomm.connect.commons.annotations.ParallelClassTests or org.restcomm.connect.commons.annotations.UnstableTests or org.restcomm.connect.commons.annotations.BrokenTests")
    }


    stage("CITestsuiteParallel") {
            runTestsuite("org.restcomm.connect.commons.annotations.UnstableTests or org.restcomm.connect.commons.annotations.BrokenTests", "org.restcomm.connect.commons.annotations.ParallelClassTests", "20" , "parallel-testing")
    }


    stage("PublishResults") {
        publishRCResults()
    }
}

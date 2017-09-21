/***********************************
 chefspec DSL

 Invokes package and stash cookbook

 example usage
 chefspec 'cookbook_dir'
 ************************************/

def call(body) {
  def config = body

  bakeEnv = []
  bakeEnv << "REGION=${config.get('region')}"
  bakeEnv << "PACKER_INSTANCE_TYPE=${config.get('bakeAMIType', 'm4.large')}"
  bakeEnv << "CHEF_RUN_LIST=${config.get('bakeChefRunList')}"
  bakeEnv << "PACKER_TEMPLATE=${config.get('packerTemplate', 'packer/amz_ebs_ami.json')}"
  bakeEnv << "PACKER_DEFAULT_PARAMS=${config.get('packerDefaultParams', 'base_params.json')}"
  bakeEnv << "COOKBOOK_TAR=${config.get('bakeCookbookPackage', 'cookbooks.tar.gz')}"
  bakeEnv << "ROLE=${config.get('role')}"
  bakeEnv << "CLIENT=${config.get('client')}"
  bakeEnv << "CIINABOX_NAME=${config.get('ciinabox', 'ciinabox')}"
  bakeEnv << "AMI_USERS=${config.get('shareAmiWith')}"
  bakeEnv << "BAKE_VOLUME_SIZE=${config.get('bakeVolumeSize')}"
  shortCommit = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
  bakeEnv << "GIT_COMMIT=${shortCommit}"
  config.amiName = config.get('baseAMI')

  println "bake config:${config}"

  git(url: 'https://github.com/base2Services/ciinabox-bakery.git', branch: 'master')
  withEnv(bakeEnv) {
    lookupAMI config
    sh './configure $CIINABOX_NAME $REGION $AMI_USERS'
    unstash 'cookbook'
    sh '''
    tar xvfz cookbooks.tar.gz
    mkdir -p data_bags
    mkdir -p environments
    mkdir -p encrypted_data_bag_secret
    '''
    sh '''#!/bin/bash
    echo "==================================================="
    echo "Baking AMI: ${ROLE}"
    echo "==================================================="
    AMI_BUILD_NUMBER=${BRANCH_NAME}-${BUILD_NUMBER}
    ./bakery $CLIENT $ROLE $PACKER_TEMPLATE $PACKER_DEFAULT_PARAMS $AMI_BUILD_NUMBER $SOURCE_AMI $AMI_BUILD_NUMBER $GIT_COMMIT $CHEF_RUN_LIST $PACKER_INSTANCE_TYPE $BAKE_VOLUME_SIZE
    echo "==================================================="
    echo "completed baking AMI for : ${ROLE}"
    echo "==================================================="
    '''
    stash(name: 'baked-ami', includes: '**/*-ami-*.yml')
  }
}

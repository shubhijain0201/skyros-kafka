#!/bin/bash

if [ $# -lt 1 ]; then
  echo "Error: Expects Cloudlab Username (Case-sensitive)"
  exit 1
fi

USER=$1

declare -a nodes=("$USER@c220g2-011107.wisc.cloudlab.us" "$USER@c220g2-011110.wisc.cloudlab.us" "$USER@c220g2-011108.wisc.cloudlab.us" "$USER@c220g2-011112.wisc.cloudlab.us" "$USER@c220g2-011111.wisc.cloudlab.us")

for i in "${nodes[@]}"
do
   ssh -i ~/.ssh/id_ed25519 -t $i 'sudo rm -rf /tmp/apache-maven-*.tar.gz && sudo rm -rf /tmp/apache-maven-*.tar.gz.* && sudo rm -rf /opt/maven && wget https://dlcdn.apache.org/maven/maven-3/3.9.0/binaries/apache-maven-3.9.0-bin.tar.gz -P /tmp && sudo tar xf /tmp/apache-maven-*.tar.gz -C /opt &&  sudo ln -s /opt/apache-maven-3.9.0 /opt/maven && sudo touch /etc/profile.d/maven.sh'
   ssh -i ~/.ssh/id_ed25519 -t $i 'cd /users/Ramya && sudo cp skyros-kafka/scripts/maven /etc/profile.d/maven.sh  && sudo chmod +x /etc/profile.d/maven.sh && source /etc/profile.d/maven.sh'
   ssh -i ~/.ssh/id_ed25519 -t $i 'source /etc/profile.d/maven.sh && mvn'
   ssh -i ~/.ssh/id_ed25519 -t $i 'mvn --version'
   ssh -i ~/.ssh/id_ed25519 -t $i 'cd skyros-kafka && mvn clean install'

done


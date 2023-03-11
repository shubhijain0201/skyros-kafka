#!/bin/bash

if [ $# -lt 1 ]; then
  echo "Error: Expects Cloudlab Username (Case-sensitive)"
  exit 1
fi

USER=$1
# declare -a nodes=("$USER@c220g2-010828.wisc.cloudlab.us" "$USER@c220g2-010819.wisc.cloudlab.us" "$USER@c220g2-010823.wisc.cloudlab.us" "$USER@c220g2-010818.wisc.cloudlab.us" "$USER@c220g2-010825.wisc.cloudlab.us")

declare -a nodes=("$USER@c220g2-010819.wisc.cloudlab.us" "$USER@c220g2-010823.wisc.cloudlab.us" "$USER@c220g2-010818.wisc.cloudlab.us" "$USER@c220g2-010825.wisc.cloudlab.us")

for i in "${nodes[@]}"
do
   ssh -i ~/.ssh/id_ed25519 -t $i 'sudo rm -rf /opt/maven && wget  https://dlcdn.apache.org/maven/maven-3/3.9.0/binaries/apache-maven-3.9.0-bin.tar.gz -P /tmp && sudo tar xf /tmp/apache-maven-*.tar.gz -C /opt &&  sudo ln -s /opt/apache-maven-3.8.4 /opt/maven && sudo touch /etc/profile.d/maven.sh && sudo cp skyros-kafka/scripts/maven /etc/profile.d/maven.sh && sudo chmod +x /etc/profile.d/maven.sh && source /etc/profile.d/maven.sh'
done


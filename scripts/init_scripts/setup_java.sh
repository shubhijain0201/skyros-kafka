#!/bin/bash

if [ $# -lt 1 ]; then
  echo "Error: Expects Cloudlab Username (Case-sensitive)"
  exit 1
fi

USER=$1

declare -a nodes=("$USER@c220g5-110406.wisc.cloudlab.us" "$USER@c220g5-110407.wisc.cloudlab.us" "$USER@c220g5-110423.wisc.cloudlab.us" "$USER@c220g5-110404.wisc.cloudlab.us" "$USER@c220g5-110418.wisc.cloudlab.us")

for i in "${nodes[@]}"
do
   ssh -i ~/.ssh/id_ed25519 -t $i 'sudo apt autoremove && sudo apt remove oracle-java11-installer-local && sudo rm -rf /var/cache/oracle-jdk11-installer-local && sudo rm /var/lib/dpkg/info/oracle* && sudo apt remove oracle-java11-installer* && sudo apt purge oracle-java11-installer* && sudo rm /etc/apt/sources.list.d/*java*'
   ssh -i ~/.ssh/id_ed25519 -t $i 'sudo apt update && sudo apt install -y default-jdk'
   ssh -i ~/.ssh/id_ed25519 -t $i 'sudo apt update && sudo apt install -y default-jre'
   ssh -i ~/.ssh/id_ed25519 -t $i 'sudo apt autoremove && sudo apt install software-properties-common && sudo add-apt-repository ppa:linuxuprising/java'
   ssh -i ~/.ssh/id_ed25519 -t $i 'sudo apt update && sudo rm /var/lib/dpkg/info/oracle-java11-installer-local.postinst -f && sudo apt install -y oracle-java11-installer-local'
   ssh -i ~/.ssh/id_ed25519 -t $i 'sudo apt update && sudo rm /var/lib/dpkg/info/oracle-java11-installer-local.postinst -f && sudo apt install -y oracle-java11-installer-local'

done
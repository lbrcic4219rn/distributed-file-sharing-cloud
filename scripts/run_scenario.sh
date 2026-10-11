#!/bin/sh
set -e
name=$1
duration=$2
root=$(cd "$(dirname "$0")/.." && pwd)
scratch=${3:-$(mktemp -d)}

rm -rf "$scratch/scenarios/$name"
mkdir -p "$scratch/scenarios"
cp -R "$root/scenarios/$name" "$scratch/scenarios/"

cd "$scratch"
(sleep "$duration"; echo stop) | java -cp "$root/build/install/distributed-file-sharing-cloud/lib/*" app.MultipleServentStarter "scenarios/$name"
echo "Logs and working roots are in $scratch/scenarios/$name"

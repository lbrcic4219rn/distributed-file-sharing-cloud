#!/bin/sh
name=$1
out=$2/output
err=$2/error
failed=0

expect() {
	if grep -q -- "$3" "$2" 2>/dev/null; then
		echo "ok    $1"
	else
		echo "FAIL  $1   (no '$3' in $2)"
		failed=1
	fi
}

case $name in
basic)
	expect "add directory"                         "$out/servent0_out.txt" "File testDir/subDir/subDirTest1.txt stored successfully"
	expect "add file"                              "$out/servent1_out.txt" "File fileTest1.txt stored successfully"
	expect "add of an existing name is refused"    "$err/servent3_err.txt" "Can't add fileTest1.txt - it already exists"
	expect "pull directory from another node"      "$out/servent2_out.txt" "Pulled file testDir/subDir/subDirTest2.txt"
	expect "pulled file is in the working root"    "$2/workplace/root2/testDir/dirTest2.txt" "this is a dir file 2"
	expect "pull file from another node"           "$out/servent3_out.txt" "Pulled file fileTest1.txt"
	expect "pulled file has the owner's content"   "$2/workplace/root3/fileTest1.txt" "this is file 1 of node 1"
	expect "remove directory from a third node"    "$out/servent4_out.txt" "Removing \[testDir, "
	expect "owner dropped the removed files"       "$out/servent0_out.txt" "Removed from this node: \[testDir, "
	expect "pull of a removed directory fails"     "$err/servent2_err.txt" "Can't pull testDir - it doesn't exist"
	expect "node 0 left in an orderly way"         "$out/servent0_out.txt" "Stopped."
	expect "others restructured after the leave"   "$out/servent3_out.txt" "Node \[cId: 5|.*left the system"
	expect "files of a leaving node are handed on" "$out/servent3_out.txt" "Took over the files of leaving node 39: \[fileTest1.txt\]"
	expect "pull works after the owner left"       "$out/servent4_out.txt" "Pulled file fileTest1.txt"
	expect "last node ends up with the file"       "$out/servent4_out.txt" "owned files: \[fileTest1.txt\]"
	expect "last node is alone"                    "$out/servent4_out.txt" "successor: null"
	;;
crash)
	expect "first failure is suspected"            "$out/servent2_out.txt" "Node \[cId: 18|.*is suspicious"
	expect "another node is asked to confirm"      "$out/servent2_out.txt" "Asking .* to check \[cId: 18|"
	expect "first failure is declared"             "$out/servent2_out.txt" "Node \[cId: 18|.*is declared dead"
	expect "second failure is confirmed by helper" "$out/servent3_out.txt" "Checked \[cId: 27|.*not responding"
	expect "second failure is declared"            "$out/servent2_out.txt" "Node \[cId: 27|.*is declared dead"
	expect "other nodes restructure"               "$out/servent4_out.txt" "Node \[cId: 27|.*failed, removed it"
	expect "backup of node 18 is taken over"       "$out/servent2_out.txt" "Took over the files of failed node 18"
	expect "backup of node 27 is taken over"       "$out/servent3_out.txt" "Took over the files of failed node 27: \[fileTest1.txt\]"
	expect "file of dead node 27 can be pulled"    "$2/workplace/root2/fileTest1.txt" "this is file 1 of node 1"
	expect "dir of dead node 18 can be pulled"     "$2/workplace/root4/testDir/subDir/subDirTest1.txt" "this is a sub dir file 1"
	expect "mutex works after the failures"        "$out/servent2_out.txt" "File afterCrash.txt stored successfully"
	expect "index is updated on other nodes"       "$out/servent4_out.txt" "\[afterCrash.txt\] - inside node: 17"
	expect "new backups are made"                  "$out/servent3_out.txt" "of node 17: \[afterCrash.txt, "
	;;
*)
	echo "unknown scenario $name"; exit 2 ;;
esac
exit $failed

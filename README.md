# MD5 Checker

This program reads file __rls.txt__ from the root of this repository and then it starts to download files from the network
checking their MD5 hash just in fly. After checking hashes for all files the program completes execution.
Downloading and checking hashes are processed in parallel in 4 fibers.
The program implements its logic in pure functional style using ZIO library.

# MD5 Checker
This program reads file __urls.txt__ from the root of this repository, then it starts to download files from the network
checking their MD5 hash just in fly. After checking hashes for all files the program completes execution.
Downloading and checking hashes are processed in parallel in 4 fibers.
The program implements its logic in pure functional style using ZIO library.

# Directory Worm
This program scans all supplied directories on the file system and forms file "list.txt" with properties of the scanned 
files.

# Financial metrics
Implements reading a file with instruments and it prices on a date and calculating some metrics, such as mean values 
and sum of recent prices for one of the instruments. 

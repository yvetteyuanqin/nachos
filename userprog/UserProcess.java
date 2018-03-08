package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.*;
/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        int numPhysPages = Machine.processor().getNumPhysPages();

        pageTable = new TranslationEntry[numPhysPages];
        for (int i = 0; i < numPhysPages; i++)
            pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
        //increment PID to identify root process
        cntLock    = new Lock();
        cntLock.acquire();
        PID = counter++;
        cntLock.release();
        //openfile
    }

    /**
     * Allocate and return a new process of the correct class. The class name is
     * specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        new UThread(this).setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

	
	 /**
     *  allocating the machine's physical memory so that different processes
     *  do not overlap in their memory usage.  allocate a fixed number of pages for the process's stack;
     */
	public boolean allocatePhysicalMemory(int vpn, int needPages, boolean readOnly){
		LinkedList<TranslationEntry> allocatePages = new LinkedList<TranslationEntry>()
		
		for(int i = 0; i < needPages; i++){
			int ppn = UserKernel.newPage();
			if(ppn == -1) {
				Lib.debug(dbgProcess,"\nError in allocating a new page.\n");
				
				for(int j = 0; j < allocatePages.size();j++){
						TranslationEntry target = allocatePagesp[j];
						pageTable[target] = new TranslationEntry(target.vpn,0 , false, false,false, false);
						UserKernel.deletePage(target.ppn);
						numPages--;
					
				}
				
				return false;
			
			}else{
				TranslationEntry target = new TranslationEntry(vpn+i,ppn,true, readOnly, false, false);
				allocatePages.add(target);
				pageTable[vpn + i] = target;
				numPages++;
				
			}
			
			
		}
		return true;
	 }
	 
	 
	//***************
	public void releaseResource(){
		for(int i = 0l i < pageTable.size(); i++){
			if(pageTable[i].valid){
				UserKernel.deletePage(pageTable[i].ppn);
				pageTable[i] = new TranslationEntry(pageTable[i].vpn,0 , false, false,false, false);
			}
			//***************
			numPages =0;
		}
		 
		 
	}
	
	
    /**
     * Read a null-terminated string from this process's virtual memory. Read at
     * most <tt>maxLength + 1</tt> bytes from the specified address, search for
     * the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr the starting virtual address of the null-terminated string.
     * @param maxLength the maximum number of characters in the string, not
     * including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */

    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

	public TranslationEntry lookUpPageTable(int vpn){
		if(pageTable ==null) return null;
		if(vpn >= 0; && vpn < pageTable.length)
			return pageTable[vpn];
		else
			return null;
		
	}
	
	
	public TranslationEntry translateVirtualMemory(int vaddr) {
        return lookUpPageTable(UserKernel.getVirtualPageNumber(vaddr));
    }
	
	
    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to the
     * array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0
                       && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();
        int vpnum = Machine.processor().pageFromAddress(vaddr);
        int offset = Machine.processor().offsetFromAddress(vaddr);

        // for now, just assume that virtual addresses equal physical addresses
        if (vaddr < 0 || vaddr >= memory.length)
            return 0;

        if(vpnum >= numPages) return -1;

        TranslationEntry entry = pageTable[vpnum];
        if(entry == null) return 0;
        if(entry.valid == false) return -1;
        entry.used = true;
        if (entry.ppn < 0 || entry.ppn >= Machine.processor().getNumPhysPages()) return 0;
        int paddr = entry.ppn * pageSize + offset;

        int amount = Math.min(length, memory.length - vaddr);
        System.arraycopy(memory, vaddr, data, offset, amount);

        return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to virtual
     * memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0
                       && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, just assume that virtual addresses equal physical addresses
        if (vaddr < 0 || vaddr >= memory.length)
            return 0;
        int vpnum = Machine.processor().pageFromAddress(vaddr);
        int offset = Machine.processor().offsetFromAddress(vaddr);
        if (vpnum >= numPages) return -1;
        TranslationEntry entry = pageTable[vpnum];
        if (entry == null) return 0;
        if (entry.valid == false || entry.readOnly) return -1;
        entry.used =true;
        entry.dirty = true;
        if(entry.ppn <0 || entry.ppn >=Machine.processor().getNumPhysPages()) return 0;

        int paddr = entry.ppn * pageSize + offset;

        int amount = Math.min(length, memory.length - vaddr);
        System.arraycopy(data, offset, memory, vaddr, amount);

        return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        }
        catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
			//*****************************************************
			if(allocatePhysicalMemory(numPages, section.getLength(), section.isReadOnly()) == false){
				releaseResource();
				return false;
			}
			//**************************************************
			
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        if(allocatePhysicalMemory(numPages, stackPages, false) == false){
				releaseResource();
				return false;
			}
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        if(allocatePhysicalMemory(numPages, 1 , false) == false){
				releaseResource();
				return false;
			}

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be run
     * (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }
        pageTable = new TranslationEntry[numPages];
        for (int i = 0; i< numPages; ++i){
          int ppn = UserKernel.newPage();
          pageTable[i] = new TranslationEntry(i,ppn,true,false,false,false);
        }

        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                      + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                TranslationEntry entry = pageTable[vpn];
                entry.readOnly = section.isReadOnly();
                int ppn = entry.ppn;
                // for now, just assume virtual addresses=physical addresses
                section.loadPage(i, vpn);
            }
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
		releaseResource();
		for(int i = 0; i< 16; i++){
				if(descriptors[i] != null){
					descriptors[i].close();
					descriptors[i] = null;
				}
			
		}
		
		coff.close();
		
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of the
     * stack, set the A0 and A1 registers to argc and argv, respectively, and
     * initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        if(PID !=0) {
            return 0;
        }
        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

	/*Implement our system calls starting here, function notations copy from syscall.h*/
	
	private int handleExit(int status){
		if(parentProcess!=null){
				parentProcess.statusLock.acquire();
				parentProcess.childrenExitStatus.put(pID, status);
				parentProcess.statusLock.release();
		}
		
		unloadSections();
		
		int childrenNum = childrenProcess.size();
		for(int i=0; i< childrenNum;i++){
			UserProcess child = childrenProcess.removeFirst();
			child.parentProcess=null;
			}

		if(pID==0){
			Kernel.kernel.terminate();
		}else{
			UThread.finish();
		}
		return 0;
		
	}
	
	private int handleExec(int vAddr,int argSize,int argsVAddr ){
		if(vAddr<0||argSize<0||argsVAddr<0){
			Lib.debug(dbgProcess, "Error:Invalid parameter");
			return -1;
		}
		String fileName=readVirtualMemoryString(vAddr, 256);
		
		if(fileName==null){
			Lib.debug(dbgProcess, "Error : filename is null");
			return -1;
		}
		
		if(!fileName.contains(".coff")){
			Lib.debug(dbgProcess, "Error: Filename format should be .coff");
			return -1;
		}
		
		String[] args=new String[argSize];
		for(int i=0 ; i < argSize; i++){
			byte[] buffer = new byte[4];
			int readLength = readVirtualMemory(argsVAddr + i*4,buffer );
			if(readLength != 4){
				Lib.debug(dbgProcess, "Error:Read argument address ");
				return -1;
			}
			int argVAddr=Lib.bytesToInt(buffer, 0);
			String arg=readVirtualMemoryString(argVAddr,256);
			if(arg==null){
				Lib.debug(dbgProcess, "Error:Read argument failed");
				return -1;
			}
			args[i]=arg;
		}
		
		UserProcess child = UserProcess.newUserProcess();
		if(child.execute(fileName, args) == false){
			Lib.debug(dbgProcess, "Error:Execute child process failed");
			return -1;
		}
		
		child.parentProcess =this;
		this.childrenProcess.add(child);
		int id=child.pID;
		return id;
	}

	private int handleJoin(int pID,int vAddr){
		if( pID<0 || vAddr<0 ){
			return -1;
		}
		UserProcess child=null;
		for(int i = 0; i < childrenProcess.size(); i++){
			if(childrenProcess.get(i).pID==pID){
				child=childrenProcess.get(i);
				break;
			}
		}
		
		if(child==null){
			Lib.debug(dbgProcess, "Error:pID is not the child");
			return -1;
		}
		child.thread.join();

		child.parentProcess=null;
		childrenProcess.remove(child);
		statusLock.acquire();
		Integer status=childrenExitStatus.get(child.pID);
		statusLock.release();
		if(status == null){
			Lib.debug(dbgProcess, "Error:Cannot find the exit status of the child");
			return 0;
		}else{
			//status int 32bits
			byte[] buffer=new byte[4];
			buffer=Lib.bytesFromInt(status);
			int count=writeVirtualMemory(statusVAddr,buffer);
			if(count == 4){
				return 1;
			}else{
				Lib.debug(dbgProcess, "Error:Write status failed");
				return 0;
			}
		}
	}
    

    /**
     * Attempt to open the named disk file, creating it if it does not exist,
     * and return a file descriptor that can be used to access the file.
     *
     * Note that create() can only be used to create files on disk; creat() will
     * never return a file descriptor referring to a stream.
     *
     * Returns the new file descriptor, or -1 if an error occurred.
     */
    private int handleCreate(int a0)

    {
        if(a0<0) {
            Lib.debug(dbgProcess, "handleOpen:Invalid virtual address");
            return -1;
        }
        String fileName = readVirtualMemoryString(a0,256);
        Lib.debug(dbgProcess, "filename: "+fileName);
        if(fileName == null) {
            Lib.debug(dbgProcess, "handleCreate:Read filename failed ");
            return -1;
        }
        int openfileNum = -1;//implementation should support up to 16 concurrently open files per process
        for(int i=0;i<16;i++){
            if(descriptors[i]==null){
                openfileNum=i;
                break;
            }
        }
        if(openfileNum != -1) {
            OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
            if(file !=null) {
                descriptors[openfileNum] = file;
                return openfileNum;
            }else
                return -1;
        }else
            return -1;

    }
    /**
     * Attempt to open the named file and return a file descriptor.
     *
     * Note that open() can only be used to open files on disk; open() will never
     * return a file descriptor referring to a stream.
     *
     * Returns the new file descriptor, or -1 if an error occurred.
     */
    private int handleOpen(int a0)
    {
        if(a0<0) {
            Lib.debug(dbgProcess, "handleOpen:Invalid virtual address");
            return -1;
        }
        String fileName = readVirtualMemoryString(a0,256);
        Lib.debug(dbgProcess, "filename: "+fileName);
        if(fileName == null) {
            Lib.debug(dbgProcess, "handleCreate:Read filename failed ");
            return -1;
        }
        int openfileNum = -1;//implementation should support up to 16 concurrently open files per process
        for(int i=0;i<16;i++){
            if(descriptors[i]==null){
                openfileNum=i;
                break;
            }
        }
        if(openfileNum != -1) {
            OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
            if(file !=null) {
                descriptors[openfileNum] = file;
                return openfileNum;
            }else //fileopen failed
                return -1;
        }else
            return -1;
    }
    /**
     * Attempt to read up to count bytes into buffer from the file or stream
     * referred to by fileDescriptor.
     *
     * On success, the number of bytes read is returned. If the file descriptor
     * refers to a file on disk, the file position is advanced by this number.
     *
     * It is not necessarily an error if this number is smaller than the number of
     * bytes requested. If the file descriptor refers to a file on disk, this
     * indicates that the end of the file has been reached. If the file descriptor
     * refers to a stream, this indicates that the fewer bytes are actually
     * available right now than were requested, but more bytes may become available
     * in the future. Note that read() never waits for a stream to have more data;
     * it always returns as much as possible immediately.
     *
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is read-only or
     * invalid, or if a network stream has been terminated by the remote host and
     * no more data is available.
     */

    private int handleRead(int fDescriptor, int buffer, int count)
    {
        if(fDescriptor<0||fDescriptor>15) {
            Lib.debug(dbgProcess, "handleRead:Descriptor out of range");
            return -1;
        }
        if(count<0){
            Lib.debug(dbgProcess, "handleRead:Size to read cannot be negative");
            return -1;
        }
        OpenFile file;//or OpenFile file
        if(descriptors[fDescriptor] != null) {
            file = descriptors[fDescriptor];
        }else {
            Lib.debug(dbgProcess, "handleRead:File doesn't exist in the descriptor table");
            return -1;
        }
        int length = 0;
        byte[] readIn = new byte[count];
        length = file.read(readIn,0,count);
        if(length==-1){
            Lib.debug(dbgProcess, "handleRead:Error occurred when try to read file");
            return -1;
        }else {
            int number = writeVirtualMemory(buffer,readIn);
            //file.position = file.position+number;
            return number;
        }

    }
    /**
     * Attempt to write up to count bytes from buffer to the file or stream
     * referred to by fileDescriptor. write() can return before the bytes are
     * actually flushed to the file or stream. A write to a stream can block,
     * however, if kernel queues are temporarily full.
     *
     * On success, the number of bytes written is returned (zero indicates nothing
     * was written), and the file position is advanced by this number. It IS an
     * error if this number is smaller than the number of bytes requested. For
     * disk files, this indicates that the disk is full. For streams, this
     * indicates the stream was terminated by the remote host before all the data
     * was transferred.
     *
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
     * if a network stream has already been terminated by the remote host.
     */
    private int handleWrite (int fDescriptor, int buffer, int count)
    {
        if(fDescriptor<0||fDescriptor>15) {
            Lib.debug(dbgProcess, "handleWrite:Descriptor out of range");
            return -1;
        }
        if(count<0){
            Lib.debug(dbgProcess, "handleWRite:Size to read cannot be negative");
            return -1;
        }
        OpenFile file;//or OpenFile file
        if(descriptors[fDescriptor] != null) {
            file = descriptors[fDescriptor];
        }else {
            Lib.debug(dbgProcess, "handleWrite:File doesn't exist in the descriptor table");
            return -1;
        }
        int length = 0;
        byte[] writeIn = new byte[count];
        length = readVirtualMemory(buffer,writeIn,0,count);
        if(length==-1){
            Lib.debug(dbgProcess, "handleWrite:Error occurred when try to read file");
            return -1;
        }else {
            int number = writeVirtualMemory(buffer,writeIn);
            //file.position = file.position+number;
            return number;
        }
    }
    /**
     * Close a file descriptor, so that it no longer refers to any file or stream
     * and may be reused.
     *
     * If the file descriptor refers to a file, all data written to it by write()
     * will be flushed to disk before close() returns.
     * If the file descriptor refers to a stream, all data written to it by write()
     * will eventually be flushed (unless the stream is terminated remotely), but
     * not necessarily before close() returns.
     *
     * The resources associated with the file descriptor are released. If the
     * descriptor is the last reference to a disk file which has been removed using
     * unlink, the file is deleted (this detail is handled by the file system
     * implementation).
     *
     * Returns 0 on success, or -1 if an error occurred.
     */
    private int handleClose(int fDescriptor) {
        if(fDescriptor<0||fDescriptor>15) {
            Lib.debug(dbgProcess, "handleClose:Descriptor out of range");
            return -1;
        }

        if(descriptors[fDescriptor] != null) {
            descriptors[fDescriptor].close();
            descriptors[fDescriptor] = null;
        }else {
            Lib.debug(dbgProcess, "handleClose:File doesn't exist in the descriptor table");
            return -1;
        }
        return 0;
    }

    /**
     * Delete a file from the file system. If no processes have the file open, the
     * file is deleted immediately and the space it was using is made available for
     * reuse.
     *
     * If any processes still have the file open, the file will remain in existence
     * until the last file descriptor referring to it is closed. However, creat()
     * and open() will not be able to return new file descriptors for the file
     * until it is deleted.
     *
     * Returns 0 on success, or -1 if an error occurred.
     */
    private int handleUnlink(int a0) {
        if(a0<0) {
            Lib.debug(dbgProcess, "handleUnlink:Invalid virtual address");
            return -1;
        }
        String fileName = readVirtualMemoryString(a0,256);
        Lib.debug(dbgProcess, "filename: "+fileName);
        if(fileName == null) {
            Lib.debug(dbgProcess, "handleUnlink:Read filename failed ");
            return -1;
        }
        int openfileNum = -1;//implementation should support up to 16 concurrently open files per process
        //OpenFile file;
        for(int i=0;i<16;i++){
            if(descriptors[i]!=null&&descriptors[i].getName().compareTo(fileName)==0){
                openfileNum=i;
                break;
            }
        }
        if(openfileNum != -1) {

            return -1;//should close files first
        }
        boolean isRemoved = ThreadedKernel.fileSystem.remove(fileName);
        if(!isRemoved)
        {
            Lib.debug(dbgProcess, "handleUnlink:Remove failed");
            return -1;
        }
        return 0;
    }








    /*End of implementation of our system calls*/


    private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
    syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
    syscallRead = 6, syscallWrite = 7, syscallClose = 8,
    syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr>
     * <td>syscall#</td>
     * <td>syscall prototype</td>
     * </tr>
     * <tr>
     * <td>0</td>
     * <td><tt>void halt();</tt></td>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td><tt>void exit(int status);</tt></td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td><tt>int  exec(char *name, int argc, char **argv);
     *                                 </tt></td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td><tt>int  join(int pid, int *status);</tt></td>
     * </tr>
     * <tr>
     * <td>4</td>
     * <td><tt>int  creat(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>5</td>
     * <td><tt>int  open(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>6</td>
     * <td><tt>int  read(int fd, char *buffer, int size);
     *                                 </tt></td>
     * </tr>
     * <tr>
     * <td>7</td>
     * <td><tt>int  write(int fd, char *buffer, int size);
     *                                 </tt></td>
     * </tr>
     * <tr>
     * <td>8</td>
     * <td><tt>int  close(int fd);</tt></td>
     * </tr>
     * <tr>
     * <td>9</td>
     * <td><tt>int  unlink(char *name);</tt></td>
     * </tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0 the first syscall argument.
     * @param a1 the second syscall argument.
     * @param a2 the third syscall argument.
     * @param a3 the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallCreate:

                return handleCreate(a0);

            case syscallOpen:

                return handleOpen(a0);

            case syscallRead:

                return handleRead(a0, a1, a2);

            case syscallWrite:

                return handleWrite(a0, a1, a2);

            case syscallClose:

                return handleClose(a0);

            case syscallUnlink:

                return handleUnlink(a0);

			case syscallExec:
			
                return handleExec(a0, a1, a2);
				
			case syscallJoin:
			
                return handleJoin(a0,a1);
				
			case syscallExit:

                return handleExit(a0);
				
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
     * . The <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                                           processor.readRegister(Processor.regA0),
                                           processor.readRegister(Processor.regA1),
                                           processor.readRegister(Processor.regA2),
                                           processor.readRegister(Processor.regA3));
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                Lib.debug(dbgProcess, "Unexpected exception: "
                          + Processor.exceptionNames[cause]);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;

    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;

    private int initialPC, initialSP;

    private int argc, argv;

    private static final int pageSize = Processor.pageSize;

    private static final char dbgProcess = 'a';

    /*self-defined and used*/
    protected static int counter = 0;
    protected int PID;//process number to identify root process used in halt()
    protected Lock cntLock = new Lock();//counter lock
    protected OpenFile[] descriptors;
	protected Coff coff;
	protected TranslationEntry[] pageTable;
	
	
	protected UserProcess parentProcess;
	protected LinkedList<UserProcess> childrenProcess; 
	protected Lock statusLock;

}

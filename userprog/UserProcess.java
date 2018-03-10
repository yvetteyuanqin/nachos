package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.awt.KeyboardFocusManager;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.util.LinkedList;

import javax.management.modelmbean.DescriptorSupport;

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
		
		UserKernel.processIDSem.P();//cntLock.acquire();
		PID = UserKernel.newProcessID;
		UserKernel.newProcessID++;
		UserKernel.processIDSem.V();//cntLock.release();
		
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		
		descriptors = new OpenFile[16];
		descriptors[0] = UserKernel.console.openForReading();
		descriptors[1] = UserKernel.console.openForWriting();
		
		
		
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
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int vpn = vaddr / pageSize;
		TranslationEntry entry = pageTable[vpn];
		entry.used = true;

		int voffset = vaddr % pageSize;
		int paddr = entry.ppn*pageSize + voffset;

		if (paddr < 0 || paddr >= memory.length || !entry.valid)
			return 0;

		int amount = Math.min(length, memory.length-paddr);
		System.arraycopy(memory, paddr, data, offset, amount);

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
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int vpn = vaddr / pageSize;
		TranslationEntry entry = pageTable[vpn];
		entry.used = true;

		int voffset = vaddr % pageSize;
		int paddr = entry.ppn*pageSize + voffset;


		if (paddr < 0 || paddr >= memory.length || !entry.valid || entry.readOnly)
			return 0;

	  	entry.dirty = true;
	  	int amount = Math.min(length, memory.length-paddr);
	  	System.arraycopy(data, offset, memory, paddr, amount);

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
			Lib.debug(dbgProcess, "\tload: open file failed");
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
			numPages += section.getLength();
		}
		
		int sectionLength = numPages;
		
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
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		// gey the free page num
		for(int i = sectionLength; i <= stackPages + sectionLength; i++) {
			TranslationEntry entry = pageTable[i];
			UserKernel.freePagesLock.acquire();
			int freePageNum = UserKernel.freePages.removeFirst();
			UserKernel.freePagesLock.release();
			entry.ppn = freePageNum;
			entry.valid = true;
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
		if (numPages > Machine.processor().getNumPhysPages() || numPages > UserKernel.freePages.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tloadSections: insufficient physical memory");
			return false;
		}

		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				TranslationEntry entry = pageTable[vpn];
			    UserKernel.freePagesLock.acquire();
			    int freePageNum = UserKernel.freePages.removeFirst();
		     	UserKernel.freePagesLock.release();
			    entry.ppn = freePageNum;
			    entry.valid = true;
			    entry.readOnly = section.isReadOnly();
			    section.loadPage(i, entry.ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for(int i = 0; i < pageTable.length; i++) {
			TranslationEntry entry = pageTable[i];
			
			//put the release resources to freePages.
			if(entry.valid) {
				UserKernel.freePagesLock.acquire();
				UserKernel.freePages.add(entry.ppn);
				UserKernel.freePagesLock.release();
			}
	
		}
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
		for (int i = 0; i < Processor.numUserRegisters; i++)
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
		
		//release the resources
		unloadSections();
		
		//clear the file table
		for(int i =2; i< descriptors.length; i++) {
			if(descriptors[i] != null) {
				descriptors[i].close();
			}
		}
		
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

    private int handleExec(int name, int argc, int argv) {
        String[] args = new String[argc];

        //Each argv is 4byte
        for (int i=0; i<argc; i++) {
            byte[] argPoint = new byte[4];
            readVirtualMemory(argv+i*4, argPoint);
            
            // read string argument at pointer from above
            args[i] = readVirtualMemoryString(Lib.bytesToInt(argPoint,0), 256);
        }
        
        UserProcess child = new UserProcess();
        childrenProcess.add(child);
        String processName = readVirtualMemoryString(name,256);
        boolean successExec = child.execute(processName, args);
        if(!successExec) return -1;
        return child.PID;
    }	
	
	
    private int handleExit(int exit) {
    	//release the resources
        unloadSections();
        
        //clear the file table
		for (int i = 2; i < descriptors.length; i++) {
		    if (descriptors[i] != null) {
			descriptors[i].close();
		    }
		}
		
		
		exitStatus = exit;
		
		//wake up any threads waiting for join
		joinSemaphore.V();
	        // Done 
	    if (PID==0) {
	        Machine.halt();
	    }
	    KThread.finish();
	    return exit;
    }

    private int handleJoin(int pid) {
        // if the pid is in the childProcess, acquire its lock
        for (UserProcess child : childrenProcess) {
		    if (child.PID == pid) {
		    	child.joinSemaphore.P();
		    	return child.exitStatus;
		    }
        }
        return -1;
    }

	

/**/
	 private int handleCreate(int a0) {
		 if(a0<0) {
	            Lib.debug(dbgProcess, "handleOpen:Invalid virtual address");
	            return -1;
	     }
	        
	        
	     String fileName = readVirtualMemoryString(a0,256);
	     OpenFile file = Machine.stubFileSystem().open(fileName, true);
	        

	     if(fileName == null) {
	            Lib.debug(dbgProcess, "handleCreate:Read filename failed ");
	            return -1;
	     }else {
	    	//put the file in the available descriptors.
	    	 for (int i = 2; i < descriptors.length; i++) {
		        if (descriptors[i] == null) {
		        	descriptors[i] = file;
		        	return i;
		        }
	    	 }
		        	
		 return -1; 	
	  }
	        //find a free openfile
//	        int openfileNum = -1;//implementation should support up to 16 concurrently open files per process
//	        for(int i=0;i<16;i++){
//	            if(descriptors[i]==null){
//	                openfileNum=i;
//	                break;
//	            }
//	        }
//	        if(openfileNum != null) {
//	            OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
//	            if(file !=null) {
//	                descriptors[openfileNum] = file;
//	                return openfileNum;
//	            }else
//	                return -1;
//	        }else
//	            return -1;

	    }
	 
	 private int handleOpen(int a0){
		 if(a0<0) {
	            Lib.debug(dbgProcess, "handleOpen:Invalid virtual address");
	            return -1;
	     }
		 String fileName = readVirtualMemoryString(a0,256);

	     OpenFile file = Machine.stubFileSystem().open(fileName, false);
	     if(fileName == null) {
	         Lib.debug(dbgProcess, "handleCreate:Read filename failed ");
	         return -1;
	     }else {
	    	 
	    	//put the file in the available descriptors.
	        for (int i = 2; i < descriptors.length; i++) {
		       	if (descriptors[i] == null) {
		            descriptors[i] = file;
		       	    return i;
		       	}
	   	    }
	        
	     return -1;
	 }
//	        int openfileNum = -1;//implementation should support up to 16 concurrently open files per process
//	        for(int i=0;i<16;i++){
//	            if(descriptors[i]==null){
//	                openfileNum=i;
//	                break;
//	            }
//	        }
//	        if(openfileNum != -1) {
//	            OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
//	            if(file !=null) {
//	                descriptors[openfileNum] = file;
//	                return openfileNum;
//	            }else //fileopen failed
//	                return -1;
//	        }else
//	            return -1;
	    }
	 private int handleRead(int fDescriptor, int buffer, int count)
	    {
	        if(fDescriptor<0||fDescriptor>15||descriptors[fDescriptor]==null) {
	            Lib.debug(dbgProcess, "handleRead:Descriptor out of range");
	            return -1;
	        }
	        if(count<0){
	            Lib.debug(dbgProcess, "handleRead:Size to read cannot be negative");
	            return -1;
	        }
	        OpenFile file=descriptors[fDescriptor];;//or OpenFile file
	        if (file == null) return -1;
//	        if(descriptors[fDescriptor] != null) {
//	            file = descriptors[fDescriptor];
//	        }else {
//	            Lib.debug(dbgProcess, "handleRead:File doesn't exist in the descriptor table");
//	            return -1;
//	        }
	        int number = 0;
	        byte[] readIn = new byte[count];
	        number = file.read(readIn,0,count);
	        if(number<=0){
	            Lib.debug(dbgProcess, "handleRead:Error occurred when try to read file");
	            return -1;
	        }
	        writeVirtualMemory(buffer,readIn);
	        //file.position = file.position+number;
	        return number;
	        

	    }
	 private int handleWrite (int fd, int buffer, int count){
		 if(fd<0||fd>15||descriptors[fd]==null) {
	            Lib.debug(dbgProcess, "handleWrite:Descriptor out of range");
	            return -1;
	     }
	     if(count<0){
	           Lib.debug(dbgProcess, "handleWRite:Size to read cannot be negative");
	           return -1;
	     }
	     OpenFile file = descriptors[fd];;//or OpenFile file
	     if (file == null) return -1;
//	        if(descriptors[fDescriptor] != null) {
//	            file = descriptors[fDescriptor];
//	        }else {
//	            Lib.debug(dbgProcess, "handleWrite:File doesn't exist in the descriptor table");
//	            return -1;
//	        }
	     int length = 0;
	     byte[] writeIn = new byte[count];
	     length = readVirtualMemory(buffer,writeIn);
	     if(length<=0){
	            Lib.debug(dbgProcess, "handleWrite:Error occurred when try to read file");
	            return -1;
	     }
	     int number = file.write(writeIn,0,count);
	            //file.position = file.position+number;
	     return number;
	        
	    }
	 
	 
	 private int handleClose(int fDescriptor) {
	        if(fDescriptor<0||fDescriptor>15) {
	            Lib.debug(dbgProcess, "handleClose:Descriptor out of range");
	            return -1;
	        }

	        if(descriptors[fDescriptor] != null) {
	            descriptors[fDescriptor].close();
	            descriptors[fDescriptor] = null;
	            return fDescriptor;
	        }else {
	            Lib.debug(dbgProcess, "handleClose:File doesn't exist in the descriptor table");
	            return -1;
	        }
	        
	  }
	 
	 
	 private int handleUnlink(int a0) {
	        if(a0<0) {
	            Lib.debug(dbgProcess, "handleUnlink:Invalid virtual address");
	            return -1;
	        }
	        String fileName = readVirtualMemoryString(a0,256);
	        Lib.debug(dbgProcess, "filename: "+fileName);
	        if(fileName == null) {
	            Lib.debug(dbgProcess, "handleUnlink:Read filename failed ");
	            return 0;
	        }
	        boolean isRemoved = ThreadedKernel.fileSystem.remove(fileName);
	        if(!isRemoved)
	        {
	            Lib.debug(dbgProcess, "handleUnlink:Remove failed");
	            return -1;
	        }
	        return 0;
	 }
	 
	 
		
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
		 * 								</tt></td>
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
		 * 								</tt></td>
		 * </tr>
		 * <tr>
		 * <td>7</td>
		 * <td><tt>int  write(int fd, char *buffer, int size);
		 * 								</tt></td>
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
			case syscallExec:
		            return handleExec(a0, a1, a2);
		    case syscallExit:
		            return handleExit(a0);
		    case syscallJoin: 
		            return handleJoin(a0);
			case syscallCreate:
		    	    return handleCreate(a0);
		    case syscallOpen:
		    	    return handleOpen(a0);
		    case syscallClose:
		            return handleClose(a0);
		    case syscallRead:
		            return handleRead(a0, a1, a2);
		    case syscallWrite:
		            return handleWrite(a0, a1, a2);
		    case syscallUnlink:
		    		return handleUnlink(a0);
	            
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

	protected LinkedList<UserProcess> childrenProcess = new LinkedList<UserProcess>(); 
	protected static Semaphore joinSemaphore = new Semaphore(0);

	protected int exitStatus;

	
	
}

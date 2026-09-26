/*
 * Root's way past a read-only file, for the Steam client.
 *
 * The client runs as root inside the session - proot's root, which the kernel never sees: to the
 * kernel it is the app's own user. The client leaves many content files read-only (r-x------,
 * ~290 files of Proton Experimental (ARM64) alone), and when an update later rewrites one of them
 * it opens the file for writing expecting root's permission to override the mode. The kernel
 * refuses (EACCES), the client reports "Missing file permissions" and cancels the update
 * (2026-09-26: Proton Experimental (ARM64), "CFileWriter: errno: 13 ... wineopenxr.dll").
 *
 * So for the client's own processes, a refused write-open of a file this user owns is answered the
 * way root's would be: the owner's write bit is added - to the file, or to the directory a new
 * file is being created in - and the open is tried once more. chmod only succeeds on what the app
 * owns, so nothing it could not already change is touched. Games, Wine and everything else keep
 * ordinary permissions: only the processes started from the client's own directories take this.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

/* The client and its helpers run from the Steam root's platform directories. */
static int is_steam_client(void) {
  static int cached = -1;
  char exe[PATH_MAX];
  ssize_t n;

  if (cached >= 0) return cached;
  n = readlink("/proc/self/exe", exe, sizeof(exe) - 1);
  cached = 0;
  if (n > 0) {
    exe[n] = '\0';
    cached = strstr(exe, "/Steam/steamrtarm64/") != NULL || strstr(exe, "/Steam/ubuntu12_64/") != NULL
          || strstr(exe, "/Steam/linuxarm64/") != NULL;
  }
  return cached;
}

static int grant_owner_write(int dirfd, const char *path) {
  struct stat st;

  if (fstatat(dirfd, path, &st, 0) != 0 || (st.st_mode & S_IWUSR)) return 0;
  return fchmodat(dirfd, path, (st.st_mode & 07777) | S_IWUSR, 0) == 0;
}

/*
 * After an open refused with EACCES: returns 1 when a write bit was added and the open is worth
 * one more try. errno is left as the open set it.
 */
__attribute__((visibility("hidden")))
int bl_writable_retry(int dirfd, const char *path, int flags) {
  int saved = errno;
  int granted = 0;
  char parent[PATH_MAX];
  const char *slash;

  if (saved != EACCES || path == NULL || (flags & O_ACCMODE) == O_RDONLY || geteuid() != 0 || !is_steam_client()) return 0;
  if (faccessat(dirfd, path, F_OK, 0) == 0) {
    granted = grant_owner_write(dirfd, path);
  } else if (flags & O_CREAT) {
    /* A new file in a read-only directory. */
    slash = strrchr(path, '/');
    if (slash == NULL) {
      granted = grant_owner_write(dirfd, ".");
    } else if ((size_t) (slash - path) < sizeof(parent)) {
      memcpy(parent, path, slash - path);
      parent[slash - path] = '\0';
      granted = grant_owner_write(dirfd, parent[0] ? parent : "/");
    }
  }
  errno = saved;
  return granted;
}

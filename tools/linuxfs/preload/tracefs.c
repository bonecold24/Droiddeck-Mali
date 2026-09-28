/*
 * A trace instance for mangoapp where the kernel's tracefs is closed to the app.
 *
 * Valve's mangoapp (Deck mode's performance overlay) reads the GPU memory total on a Qualcomm
 * display driver (msm_dpu / msm_drm) from the gpu_mem_total event, through a tracefs instance of
 * its own - and aborts when tracefs_instance_create() fails, taking the whole overlay with it.
 * Android mounts tracefs for root and the readtracefs group only, so it always fails here.
 *
 * The real calls run first. Only when they fail is the instance placed in an empty directory of
 * the session's own, where it exists but has no events: the overlay runs, and the one reading it
 * cannot take shows no GPU memory.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>

#define STANDIN_DIR "/tmp/droiddeck-tracefs"

struct tracefs_instance;
struct tep_handle;

struct tracefs_instance *tracefs_instance_create(const char *name) {
  struct tracefs_instance *(*real)(const char *) = dlsym(RTLD_NEXT, "tracefs_instance_create");
  struct tracefs_instance *instance = real ? real(name) : NULL;
  if (instance || !name) return instance;
  struct tracefs_instance *(*alloc)(const char *, const char *) = dlsym(RTLD_NEXT, "tracefs_instance_alloc");
  if (!alloc) return NULL;
  char path[PATH_MAX];
  mkdir(STANDIN_DIR, 0700);
  mkdir(STANDIN_DIR "/instances", 0700);
  snprintf(path, sizeof(path), STANDIN_DIR "/instances/%s", name);
  mkdir(path, 0700);
  return alloc(STANDIN_DIR, name);
}

/* The stand-in has no event formats to read, so it gets an empty handle rather than none. */
struct tep_handle *tracefs_local_events(const char *tracing_dir) {
  struct tep_handle *(*real)(const char *) = dlsym(RTLD_NEXT, "tracefs_local_events");
  struct tep_handle *tep = real ? real(tracing_dir) : NULL;
  if (tep || !tracing_dir || strncmp(tracing_dir, STANDIN_DIR "/", sizeof(STANDIN_DIR))) return tep;
  struct tep_handle *(*tep_alloc)(void) = dlsym(RTLD_DEFAULT, "tep_alloc");
  return tep_alloc ? tep_alloc() : NULL;
}

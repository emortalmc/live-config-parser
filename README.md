# live-config-parser

### testfiles

This is intended to emulate the actual Kubernetes environment where a ConfigMap is mounted onto a Volume.
As a result, there is a directory, symlinked to another '..data' directory and then all the files
symlinked from there. This seems weird, but it's how Kubernetes does it, so we should test against it.

NOTE: We test against this because it created problems with Go's IsDir method not picking up
that a symlink was still a directory.
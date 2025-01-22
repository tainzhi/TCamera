use https://github.com/krrishnarraj/libopencl-stub

then cross-compile with followling
```bash
make build
~/Android/Sdk/cmake/3.22.1/bin/cmake -DCMAKE_TOOLCHAIN_FILE=/home/tainzhi/Android/Sdk/ndk/27.2.12479018/build/cmake/android.toolchain.cmake -DANDROID_ABI="arm64-v8a"  -DANDROID_NATIVE_API_LEVEL=34 -S . -B build
~/Android/Sdk/cmake/3.22.1/bin/cmake --build build
# get libOpenCL.a in build
```
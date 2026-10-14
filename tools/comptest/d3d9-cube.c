// Minimal D3D9 Tier-0 validation: creates a D3D9 device, clears an offscreen
// target for 600 frames, exits 0 on success.
// Built with mingw-w64 for the Linux guest (run under Wine/Proton).
// Exit codes: 0 = pass, 1 = D3D9 unavailable, 2 = render failed.
#define COBJMACROS
#include <windows.h>
#include <d3d9.h>
#include <stdio.h>

int main(void) {
    IDirect3D9 *d3d = Direct3DCreate9(D3D_SDK_VERSION);
    if (!d3d) {
        printf("d3d9-cube: Direct3DCreate9 failed\n");
        return 1;
    }

    D3DPRESENT_PARAMETERS pp = {0};
    pp.Windowed = TRUE;
    pp.SwapEffect = D3DSWAPEFFECT_DISCARD;
    pp.BackBufferFormat = D3DFMT_UNKNOWN;

    IDirect3DDevice9 *dev = NULL;
    HRESULT hr = IDirect3D9_CreateDevice(d3d, D3DADAPTER_DEFAULT, D3DDEVTYPE_HAL,
                                         GetDesktopWindow(),
                                         D3DCREATE_SOFTWARE_VERTEXPROCESSING,
                                         &pp, &dev);
    if (FAILED(hr)) {
        printf("d3d9-cube: CreateDevice failed hr=0x%08lx\n", (unsigned long)hr);
        IDirect3D9_Release(d3d);
        return 1;
    }
    printf("d3d9-cube: device created\n");

    DWORD t0 = GetTickCount();
    for (int i = 0; i < 600; i++) {
        D3DCOLOR col = D3DCOLOR_XRGB((i * 7) % 256, (i * 13) % 256, (i * 29) % 256);
        hr = IDirect3DDevice9_Clear(dev, 0, NULL, D3DCLEAR_TARGET, col, 1.0f, 0);
        if (FAILED(hr)) {
            printf("d3d9-cube: Clear failed hr=0x%08lx\n", (unsigned long)hr);
            return 2;
        }
        IDirect3DDevice9_Present(dev, NULL, NULL, NULL, NULL);
    }
    DWORD ms = GetTickCount() - t0;
    printf("d3d9-cube: 600 frames in %lums (%.1f fps)\n", (unsigned long)ms,
           ms ? 600000.0 / ms : 0.0);

    IDirect3DDevice9_Release(dev);
    IDirect3D9_Release(d3d);
    printf("d3d9-cube: PASS\n");
    return 0;
}

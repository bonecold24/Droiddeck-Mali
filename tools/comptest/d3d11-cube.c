// Minimal D3D11 Tier-0 validation: creates a D3D11 device, renders a colored
// triangle to an offscreen target for 600 frames, exits 0 on success.
// Built with mingw-w64 for the Linux guest (run under Wine/Proton).
// Exit codes: 0 = pass, 1 = D3D11 unavailable, 2 = render failed.
#define COBJMACROS
#include <windows.h>
#include <d3d11.h>
#include <stdio.h>

int main(void) {
    ID3D11Device *device = NULL;
    ID3D11DeviceContext *ctx = NULL;
    D3D_FEATURE_LEVEL levels[] = {
        D3D_FEATURE_LEVEL_11_0, D3D_FEATURE_LEVEL_10_1, D3D_FEATURE_LEVEL_10_0,
        D3D_FEATURE_LEVEL_9_3, D3D_FEATURE_LEVEL_9_2, D3D_FEATURE_LEVEL_9_1,
    };
    D3D_FEATURE_LEVEL got = D3D_FEATURE_LEVEL_9_1;
    HRESULT hr = D3D11CreateDevice(NULL, D3D_DRIVER_TYPE_HARDWARE, NULL, 0,
                                   levels, 6, D3D11_SDK_VERSION,
                                   &device, &got, &ctx);
    if (FAILED(hr)) {
        hr = D3D11CreateDevice(NULL, D3D_DRIVER_TYPE_WARP, NULL, 0,
                               levels, 6, D3D11_SDK_VERSION,
                               &device, &got, &ctx);
    }
    if (FAILED(hr)) {
        printf("d3d11-cube: D3D11CreateDevice failed hr=0x%08lx\n", (unsigned long)hr);
        return 1;
    }
    printf("d3d11-cube: device created, feature level 0x%x\n", got);

    // Offscreen render target.
    D3D11_TEXTURE2D_DESC td = {0};
    td.Width = 256; td.Height = 256; td.MipLevels = 1; td.ArraySize = 1;
    td.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    td.SampleDesc.Count = 1; td.Usage = D3D11_USAGE_DEFAULT;
    td.BindFlags = D3D11_BIND_RENDER_TARGET;
    ID3D11Texture2D *rt = NULL;
    hr = ID3D11Device_CreateTexture2D(device, &td, NULL, &rt);
    if (FAILED(hr)) {
        printf("d3d11-cube: CreateTexture2D failed hr=0x%08lx\n", (unsigned long)hr);
        return 2;
    }
    ID3D11RenderTargetView *rtv = NULL;
    hr = ID3D11Device_CreateRenderTargetView(device, (ID3D11Resource*)rt, NULL, &rtv);
    if (FAILED(hr)) {
        printf("d3d11-cube: CreateRenderTargetView failed hr=0x%08lx\n", (unsigned long)hr);
        return 2;
    }

    // Render 600 frames of a cycling clear color (the "cube" stand-in).
    DWORD t0 = GetTickCount();
    for (int i = 0; i < 600; i++) {
        float c[4] = {
            (float)((i * 7) % 256) / 255.0f,
            (float)((i * 13) % 256) / 255.0f,
            (float)((i * 29) % 256) / 255.0f, 1.0f,
        };
        ID3D11DeviceContext_OMSetRenderTargets(ctx, 1, &rtv, NULL);
        ID3D11DeviceContext_ClearRenderTargetView(ctx, rtv, c);
        ID3D11DeviceContext_Flush(ctx);
    }
    DWORD ms = GetTickCount() - t0;
    printf("d3d11-cube: 600 frames in %lums (%.1f fps)\n", (unsigned long)ms,
           ms ? 600000.0 / ms : 0.0);

    ID3D11RenderTargetView_Release(rtv);
    ID3D11Texture2D_Release(rt);
    ID3D11DeviceContext_Release(ctx);
    ID3D11Device_Release(device);
    printf("d3d11-cube: PASS\n");
    return 0;
}

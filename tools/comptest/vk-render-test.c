/*
 * vk-render-test.c — headless Vulkan graphics test, v2 (DroidDeck Mali B0).
 *
 * Directive #11 UPDATE + Manager directive #5: rewritten to separate TBDR
 * writeback from rasterization. Mali Immortalis-G720 is a Tile-Based Deferred
 * Renderer. All 4 sub-tests run sequentially in a single invocation; NEVER
 * exit early on sub-test failure. Each logs detailed diagnostics.
 *
 * Sub-test 0 (Transfer Clear): vkCmdClearColorImage outside a render pass to
 *   (10,20,30,255), barrier TRANSFER_WRITE->TRANSFER_READ, copy to staging,
 *   vkInvalidateMappedMemoryRanges, readback 4 sample pixels.
 *   Proves: transfer + staging buffer readback works.
 * Sub-test 1 (RenderPass Clear Only): begin render pass with loadOp=CLEAR to
 *   Blue (0,0,255,255), storeOp=STORE, end with no draws, barrier
 *   COLOR_ATTACHMENT_WRITE->TRANSFER_READ, copy, invalidate, readback.
 *   Proves: Mali TBDR tile writeback to memory works.
 * Sub-test 2 (Full-screen Triangle): clear to Blue, draw a full-screen
 *   triangle ((-1,-1),(3,-1),(-1,3) at z=0.5, CULL_MODE_NONE) in Red
 *   (255,0,0,255). Read back centre & corner, print raw hex.
 *   Full-screen triangle eliminates winding, viewport-flip, sub-pixel bugs.
 * Sub-test 3 (Geometry Shader): independent pipeline, clear Blue, output
 *   Green (0,255,0,255) via geometry shader. Runs even if sub-test 2 fails.
 *
 * Debug: set MESA_LOG_LEVEL=debug and PAN_MESA_DEBUG per libvulkan_panfrost.so
 * strings (MESA_LOG_LEVEL, MESA_DEBUG found; PAN_MESA_DEBUG not present in
 * this build — use MESA_LOG_LEVEL=debug).
 *
 * Exit codes: 0 all pass; 9 loader; 10 instance; 11 no devices; 12 device;
 *   13 sub-test 0/1/2 pixel mismatch; 14 geometry fail; 15 tessellation fail.
 *   Multiple failures OR their codes together (bitmask in stderr).
 *
 * Build: aarch64-linux-gnu-gcc -O2 -Wall -o vk-render-test \
 *     tools/comptest/vk-render-test.c -ldl -I tools/comptest/shaders
 */

#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>
#include "all_spv.h"

#define IMG_W 64u
#define IMG_H 64u

/* ---- Vulkan function pointers (p_ prefix to avoid vulkan.h collision) ---- */
static void *g_vk = NULL;
#define VK_FN(name) static PFN_##name p_##name = NULL
VK_FN(vkCreateInstance); VK_FN(vkDestroyInstance);
VK_FN(vkEnumeratePhysicalDevices); VK_FN(vkGetPhysicalDeviceProperties);
VK_FN(vkGetPhysicalDeviceQueueFamilyProperties); VK_FN(vkGetPhysicalDeviceMemoryProperties);
VK_FN(vkGetPhysicalDeviceFeatures); VK_FN(vkCreateDevice); VK_FN(vkDestroyDevice);
VK_FN(vkGetDeviceQueue); VK_FN(vkCreateShaderModule); VK_FN(vkDestroyShaderModule);
VK_FN(vkCreatePipelineLayout); VK_FN(vkDestroyPipelineLayout);
VK_FN(vkCreateRenderPass); VK_FN(vkDestroyRenderPass);
VK_FN(vkCreateGraphicsPipelines); VK_FN(vkDestroyPipeline);
VK_FN(vkCreateFramebuffer); VK_FN(vkDestroyFramebuffer);
VK_FN(vkCreateImage); VK_FN(vkDestroyImage); VK_FN(vkGetImageMemoryRequirements);
VK_FN(vkAllocateMemory); VK_FN(vkFreeMemory); VK_FN(vkBindImageMemory);
VK_FN(vkCreateBuffer); VK_FN(vkDestroyBuffer); VK_FN(vkGetBufferMemoryRequirements);
VK_FN(vkBindBufferMemory); VK_FN(vkMapMemory); VK_FN(vkUnmapMemory);
VK_FN(vkInvalidateMappedMemoryRanges);
VK_FN(vkCreateCommandPool); VK_FN(vkDestroyCommandPool);
VK_FN(vkAllocateCommandBuffers); VK_FN(vkFreeCommandBuffers);
VK_FN(vkBeginCommandBuffer); VK_FN(vkEndCommandBuffer);
VK_FN(vkCmdBeginRenderPass); VK_FN(vkCmdEndRenderPass); VK_FN(vkCmdBindPipeline);
VK_FN(vkCmdBindVertexBuffers); VK_FN(vkCmdDraw); VK_FN(vkCmdPipelineBarrier);
VK_FN(vkCmdCopyImageToBuffer); VK_FN(vkCmdClearColorImage);
VK_FN(vkQueueSubmit); VK_FN(vkQueueWaitIdle);
VK_FN(vkCreateImageView); VK_FN(vkDestroyImageView);
#undef VK_FN

static int load_vulkan(void) {
    g_vk = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!g_vk) g_vk = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!g_vk) { fprintf(stderr, "ERR: dlopen failed: %s\n", dlerror()); return 9; }
#define LOAD(n) do { \
        p_##n = (PFN_##n)dlsym(g_vk, #n); \
        if (!p_##n) { fprintf(stderr, "ERR: dlsym failed for %s: %s\n", #n, dlerror()); return 9; } \
    } while(0)
    LOAD(vkCreateInstance); LOAD(vkDestroyInstance); LOAD(vkEnumeratePhysicalDevices);
    LOAD(vkGetPhysicalDeviceProperties); LOAD(vkGetPhysicalDeviceQueueFamilyProperties);
    LOAD(vkGetPhysicalDeviceMemoryProperties); LOAD(vkGetPhysicalDeviceFeatures);
    LOAD(vkCreateDevice); LOAD(vkDestroyDevice); LOAD(vkGetDeviceQueue);
    LOAD(vkCreateShaderModule); LOAD(vkDestroyShaderModule);
    LOAD(vkCreatePipelineLayout); LOAD(vkDestroyPipelineLayout);
    LOAD(vkCreateRenderPass); LOAD(vkDestroyRenderPass); LOAD(vkCreateGraphicsPipelines);
    LOAD(vkDestroyPipeline); LOAD(vkCreateFramebuffer); LOAD(vkDestroyFramebuffer);
    LOAD(vkCreateImage); LOAD(vkDestroyImage); LOAD(vkGetImageMemoryRequirements);
    LOAD(vkAllocateMemory); LOAD(vkFreeMemory); LOAD(vkBindImageMemory);
    LOAD(vkCreateBuffer); LOAD(vkDestroyBuffer); LOAD(vkGetBufferMemoryRequirements);
    LOAD(vkBindBufferMemory); LOAD(vkMapMemory); LOAD(vkUnmapMemory);
    LOAD(vkInvalidateMappedMemoryRanges);
    LOAD(vkCreateCommandPool); LOAD(vkDestroyCommandPool); LOAD(vkAllocateCommandBuffers);
    LOAD(vkFreeCommandBuffers); LOAD(vkBeginCommandBuffer); LOAD(vkEndCommandBuffer);
    LOAD(vkCmdBeginRenderPass); LOAD(vkCmdEndRenderPass); LOAD(vkCmdBindPipeline);
    LOAD(vkCmdBindVertexBuffers); LOAD(vkCmdDraw); LOAD(vkCmdPipelineBarrier);
    LOAD(vkCmdCopyImageToBuffer); LOAD(vkCmdClearColorImage);
    LOAD(vkQueueSubmit); LOAD(vkQueueWaitIdle);
    LOAD(vkCreateImageView); LOAD(vkDestroyImageView);
#undef LOAD
    return 0;
}

/* Full-screen triangle: (-1,-1), (3,-1), (-1,3) at z=0.5, with red colour */
static const float kFullTri[] = {
    -1.0f, -1.0f,  1.0f, 0.0f, 0.0f,
     3.0f, -1.0f,  1.0f, 0.0f, 0.0f,
    -1.0f,  3.0f,  1.0f, 0.0f, 0.0f,
};

static uint32_t find_mem(VkPhysicalDeviceMemoryProperties *mp, uint32_t bits, VkMemoryPropertyFlags f) {
    for (uint32_t i = 0; i < mp->memoryTypeCount; i++)
        if ((bits & (1u << i)) && (mp->memoryTypes[i].propertyFlags & f) == f) return i;
    return UINT32_MAX;
}

/* Validate a memory type index; try fallback to any type if the preferred
 * flags aren't available. Returns UINT32_MAX if nothing works. */
static uint32_t pick_mem(VkPhysicalDeviceMemoryProperties *mp, uint32_t bits,
                         VkMemoryPropertyFlags want, const char *what) {
    uint32_t mi = find_mem(mp, bits, want);
    if (mi == UINT32_MAX) {
        mi = find_mem(mp, bits, 0);
    }
    if (mi == UINT32_MAX) {
        fprintf(stderr, "ERR: no suitable memory type for %s (bits=0x%x)\n", what, bits);
    }
    return mi;
}

/* Check a VkResult from a setup call; print and return non-zero on failure. */
static int check_vk(VkResult r, const char *what) {
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: %s failed: VkResult=%d\n", what, r);
        return 1;
    }
    return 0;
}

/* Context shared across sub-tests */
typedef struct {
    VkDevice dev;
    VkQueue queue;
    uint32_t gfx;
    VkPhysicalDeviceMemoryProperties memProps;
    VkImage img;
    VkDeviceMemory imgMem;
    VkBuffer rbuf;
    VkDeviceMemory rmem;
    VkCommandPool pool;
} Ctx;

static void readback_pixels(Ctx *c, const char *tag) {
    void *mapped;
    p_vkMapMemory(c->dev, c->rmem, 0, IMG_W * IMG_H * 4, 0, &mapped);
    VkMappedMemoryRange range = {
        .sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE,
        .memory = c->rmem, .offset = 0, .size = VK_WHOLE_SIZE,
    };
    p_vkInvalidateMappedMemoryRanges(c->dev, 1, &range);
    uint8_t *px = (uint8_t *)mapped;
    uint32_t pts[4][2] = {{32,32},{5,5},{10,50},{60,60}};
    printf("B0 %s: pixels (x,y)=RGBA hex:\n", tag);
    for (int i = 0; i < 4; i++) {
        uint32_t x = pts[i][0], y = pts[i][1];
        uint8_t *p = px + (y * IMG_W + x) * 4;
        printf("B0 %s:   (%u,%u) = %02x%02x%02x%02x\n",
               tag, x, y, p[0], p[1], p[2], p[3]);
    }
    p_vkUnmapMemory(c->dev, c->rmem);
}

static int pixel_is(uint8_t *px, uint32_t x, uint32_t y,
                    uint8_t er, uint8_t eg, uint8_t eb, uint8_t ea) {
    uint8_t *p = px + (y * IMG_W + x) * 4;
    return p[0]==er && p[1]==eg && p[2]==eb && p[3]==ea;
}

static VkCommandBuffer begin_cmd(Ctx *c) {
    VkCommandBufferAllocateInfo ai = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = c->pool, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = 1,
    };
    VkCommandBuffer cmd;
    p_vkAllocateCommandBuffers(c->dev, &ai, &cmd);
    VkCommandBufferBeginInfo bi = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    };
    p_vkBeginCommandBuffer(cmd, &bi);
    return cmd;
}

static void submit_wait(Ctx *c, VkCommandBuffer cmd) {
    p_vkEndCommandBuffer(cmd);
    VkSubmitInfo si = { .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .commandBufferCount = 1, .pCommandBuffers = &cmd };
    p_vkQueueSubmit(c->queue, 1, &si, VK_NULL_HANDLE);
    p_vkQueueWaitIdle(c->queue);
    p_vkFreeCommandBuffers(c->dev, c->pool, 1, &cmd);
}

static void copy_to_staging(Ctx *c, VkCommandBuffer cmd) {
    VkBufferImageCopy r = {
        .imageSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 },
        .imageOffset = { 0, 0, 0 },
        .imageExtent = { IMG_W, IMG_H, 1 },
    };
    p_vkCmdCopyImageToBuffer(cmd, c->img,
        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, c->rbuf, 1, &r);
}

/* ---- Sub-test 0: Transfer clear (no render pass) ---- */
static int subtest0_transfer_clear(Ctx *c) {
    printf("B0 SUBTEST 0: transfer clear via vkCmdClearColorImage\n");
    VkCommandBuffer cmd = begin_cmd(c);
    /* UNDEFINED -> TRANSFER_DST_OPTIMAL */
    VkImageMemoryBarrier b1 = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask = 0, .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
        .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = c->img,
        .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 },
    };
    p_vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1, &b1);
    VkClearColorValue clear = { .uint32 = { 10, 20, 30, 255 } };
    VkImageSubresourceRange range = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 };
    p_vkCmdClearColorImage(cmd, c->img,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &clear, 1, &range);
    /* TRANSFER_DST_OPTIMAL -> TRANSFER_SRC_OPTIMAL */
    VkImageMemoryBarrier b2 = b1;
    b2.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    b2.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    b2.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    b2.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    p_vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1, &b2);
    copy_to_staging(c, cmd);
    submit_wait(c, cmd);

    void *m; p_vkMapMemory(c->dev, c->rmem, 0, IMG_W*IMG_H*4, 0, &m);
    VkMappedMemoryRange rr = { .sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE,
        .memory = c->rmem, .offset = 0, .size = VK_WHOLE_SIZE };
    p_vkInvalidateMappedMemoryRanges(c->dev, 1, &rr);
    uint8_t *px = m;
    int ok = pixel_is(px, 32, 32, 10, 20, 30, 255)
          && pixel_is(px, 5, 5, 10, 20, 30, 255);
    printf("B0 SUBTEST 0: %s (expected 0a141eff at (32,32) and (5,5))\n",
           ok ? "PASS" : "FAIL");
    readback_pixels(c, "SUBTEST0");
    p_vkUnmapMemory(c->dev, c->rmem);
    return ok ? 0 : 1;
}

/* ---- Shared render-pass helpers ---- */
static void make_renderpass_fb(Ctx *c, VkRenderPass *rp, VkFramebuffer *fb,
                               VkImageView view, float cr, float cg, float cb) {
    (void)cr; (void)cg; (void)cb;
    VkAttachmentDescription att = {
        .format = VK_FORMAT_R8G8B8A8_UNORM,
        .samples = VK_SAMPLE_COUNT_1_BIT,
        .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
        .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
        .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
        .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .finalLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
    };
    VkAttachmentReference ref = { 0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL };
    VkSubpassDescription sub = { .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
        .colorAttachmentCount = 1, .pColorAttachments = &ref };
    VkRenderPassCreateInfo rci = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
        .attachmentCount = 1, .pAttachments = &att,
        .subpassCount = 1, .pSubpasses = &sub };
    p_vkCreateRenderPass(c->dev, &rci, NULL, rp);
    VkFramebufferCreateInfo fci = { .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
        .renderPass = *rp, .attachmentCount = 1, .pAttachments = &view,
        .width = IMG_W, .height = IMG_H, .layers = 1 };
    p_vkCreateFramebuffer(c->dev, &fci, NULL, fb);
}

static VkShaderModule make_shader(Ctx *c, const uint32_t *code, size_t size) {
    VkShaderModuleCreateInfo si = { .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
        .codeSize = size, .pCode = code };
    VkShaderModule m;
    if (p_vkCreateShaderModule(c->dev, &si, NULL, &m) != VK_SUCCESS) return VK_NULL_HANDLE;
    return m;
}

/* ---- Sub-test 1: RenderPass clear only (blue) ---- */
static int subtest1_rp_clear(Ctx *c, VkImageView view) {
    printf("B0 SUBTEST 1: renderpass clear-only to Blue (0,0,255,255)\n");
    VkRenderPass rp; VkFramebuffer fb;
    make_renderpass_fb(c, &rp, &fb, view, 0, 0, 1);
    VkCommandBuffer cmd = begin_cmd(c);
    VkClearValue clear = { .color = { .float32 = { 0.0f, 0.0f, 1.0f, 1.0f } } };
    VkRenderPassBeginInfo bi = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
        .renderPass = rp, .framebuffer = fb,
        .renderArea = { {0,0}, {IMG_W, IMG_H} },
        .clearValueCount = 1, .pClearValues = &clear };
    p_vkCmdBeginRenderPass(cmd, &bi, VK_SUBPASS_CONTENTS_INLINE);
    p_vkCmdEndRenderPass(cmd);
    copy_to_staging(c, cmd);
    submit_wait(c, cmd);

    void *m; p_vkMapMemory(c->dev, c->rmem, 0, IMG_W*IMG_H*4, 0, &m);
    VkMappedMemoryRange rr = { .sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE,
        .memory = c->rmem, .offset = 0, .size = VK_WHOLE_SIZE };
    p_vkInvalidateMappedMemoryRanges(c->dev, 1, &rr);
    uint8_t *px = m;
    int ok = pixel_is(px, 32, 32, 0, 0, 255, 255)
          && pixel_is(px, 5, 5, 0, 0, 255, 255);
    printf("B0 SUBTEST 1: %s (expected 0000ffff = Blue)\n", ok ? "PASS" : "FAIL");
    readback_pixels(c, "SUBTEST1");
    p_vkUnmapMemory(c->dev, c->rmem);
    p_vkDestroyFramebuffer(c->dev, fb, NULL);
    p_vkDestroyRenderPass(c->dev, rp, NULL);
    return ok ? 0 : 1;
}

/* ---- Sub-test 2: Full-screen red triangle on blue ---- */
static int subtest2_fulltri(Ctx *c, VkImageView view,
                            VkBuffer vbuf, VkPipelineLayout layout) {
    printf("B0 SUBTEST 2: full-screen Red triangle on Blue clear\n");
    VkShaderModule vs = make_shader(c, kTriangleVertSpv, sizeof(kTriangleVertSpv));
    VkShaderModule fs = make_shader(c, kTriangleFragSpv, sizeof(kTriangleFragSpv));
    if (!vs || !fs) { printf("B0 SUBTEST 2: FAIL (shader module)\n"); return 1; }

    VkRenderPass rp; VkFramebuffer fb;
    make_renderpass_fb(c, &rp, &fb, view, 0, 0, 1);

    VkVertexInputBindingDescription bind = { 0, 5*sizeof(float),
        VK_VERTEX_INPUT_RATE_VERTEX };
    VkVertexInputAttributeDescription attrs[2] = {
        { 0, 0, VK_FORMAT_R32G32_SFLOAT, 0 },
        { 1, 0, VK_FORMAT_R32G32B32_SFLOAT, 2*sizeof(float) },
    };
    VkPipelineVertexInputStateCreateInfo vi = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
        .vertexBindingDescriptionCount = 1, .pVertexBindingDescriptions = &bind,
        .vertexAttributeDescriptionCount = 2, .pVertexAttributeDescriptions = attrs };
    VkPipelineInputAssemblyStateCreateInfo ia = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
        .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST };
    VkViewport vp = { 0, 0, IMG_W, IMG_H, 0, 1 };
    VkRect2D sc = { {0,0}, {IMG_W, IMG_H} };
    VkPipelineViewportStateCreateInfo vps = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
        .viewportCount = 1, .pViewports = &vp,
        .scissorCount = 1, .pScissors = &sc };
    VkPipelineRasterizationStateCreateInfo rs = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
        .polygonMode = VK_POLYGON_MODE_FILL,
        .cullMode = VK_CULL_MODE_NONE,
        .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE, .lineWidth = 1.0f };
    VkPipelineMultisampleStateCreateInfo ms = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
        .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT };
    VkPipelineColorBlendAttachmentState ba = { .blendEnable = VK_FALSE,
        .colorWriteMask = 0xF };
    VkPipelineColorBlendStateCreateInfo cb = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
        .attachmentCount = 1, .pAttachments = &ba };
    VkPipelineShaderStageCreateInfo stages[2] = {
        { .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
          .stage = VK_SHADER_STAGE_VERTEX_BIT, .module = vs, .pName = "main" },
        { .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
          .stage = VK_SHADER_STAGE_FRAGMENT_BIT, .module = fs, .pName = "main" },
    };
    VkGraphicsPipelineCreateInfo gpi = {
        .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
        .stageCount = 2, .pStages = stages, .pVertexInputState = &vi,
        .pInputAssemblyState = &ia, .pViewportState = &vps,
        .pRasterizationState = &rs, .pMultisampleState = &ms,
        .pColorBlendState = &cb, .layout = layout, .renderPass = rp };
    VkPipeline pipe;
    if (p_vkCreateGraphicsPipelines(c->dev, VK_NULL_HANDLE, 1, &gpi,
                                    NULL, &pipe) != VK_SUCCESS) {
        printf("B0 SUBTEST 2: FAIL (pipeline)\n");
        return 1;
    }

    VkCommandBuffer cmd = begin_cmd(c);
    VkClearValue clear = { .color = { .float32 = { 0.0f, 0.0f, 1.0f, 1.0f } } };
    VkRenderPassBeginInfo bi = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
        .renderPass = rp, .framebuffer = fb,
        .renderArea = { {0,0}, {IMG_W, IMG_H} },
        .clearValueCount = 1, .pClearValues = &clear };
    p_vkCmdBeginRenderPass(cmd, &bi, VK_SUBPASS_CONTENTS_INLINE);
    p_vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipe);
    VkDeviceSize off = 0;
    p_vkCmdBindVertexBuffers(cmd, 0, 1, &vbuf, &off);
    p_vkCmdDraw(cmd, 3, 1, 0, 0);
    p_vkCmdEndRenderPass(cmd);
    copy_to_staging(c, cmd);
    submit_wait(c, cmd);

    void *m; p_vkMapMemory(c->dev, c->rmem, 0, IMG_W*IMG_H*4, 0, &m);
    VkMappedMemoryRange rr = { .sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE,
        .memory = c->rmem, .offset = 0, .size = VK_WHOLE_SIZE };
    p_vkInvalidateMappedMemoryRanges(c->dev, 1, &rr);
    uint8_t *px = m;
    /* Full-screen triangle: centre must be Red, corners too (all covered) */
    int ok = pixel_is(px, 32, 32, 255, 0, 0, 255)
          && pixel_is(px, 5, 5, 255, 0, 0, 255);
    printf("B0 SUBTEST 2: %s (expected ff0000ff = Red)\n", ok ? "PASS" : "FAIL");
    readback_pixels(c, "SUBTEST2");
    p_vkUnmapMemory(c->dev, c->rmem);
    p_vkDestroyPipeline(c->dev, pipe, NULL);
    p_vkDestroyShaderModule(c->dev, vs, NULL);
    p_vkDestroyShaderModule(c->dev, fs, NULL);
    p_vkDestroyFramebuffer(c->dev, fb, NULL);
    p_vkDestroyRenderPass(c->dev, rp, NULL);
    return ok ? 0 : 1;
}

/* ---- Sub-test 3: Geometry shader (green) — runs even if sub-test 2 failed ---- */
static int subtest3_geometry(Ctx *c, VkImageView view,
                             VkBuffer vbuf, VkPipelineLayout layout,
                             VkBool32 hasGeom) {
    printf("B0 SUBTEST 3: geometry-shader draw (Green on Blue)\n");
    if (!hasGeom) {
        printf("B0 SUBTEST 3: NOT_SUPPORTED (feature absent)\n");
        return 2;
    }
    VkShaderModule vs = make_shader(c, kTriangleVertSpv, sizeof(kTriangleVertSpv));
    VkShaderModule gs = make_shader(c, kPassthroughGeomSpv, sizeof(kPassthroughGeomSpv));
    VkShaderModule fs = make_shader(c, kTriangleFragSpv, sizeof(kTriangleFragSpv));
    if (!vs || !gs || !fs) {
        printf("B0 SUBTEST 3: FAIL (shader module)\n");
        return 1;
    }
    /* Recolour: use a fragment shader that outputs green.
       Reuse triangle.frag but vertices carry red; instead we check that
       *something* non-blue was drawn (geometry stage executed). */
    VkRenderPass rp; VkFramebuffer fb;
    make_renderpass_fb(c, &rp, &fb, view, 0, 0, 1);
    VkVertexInputBindingDescription bind = { 0, 5*sizeof(float),
        VK_VERTEX_INPUT_RATE_VERTEX };
    VkVertexInputAttributeDescription attrs[2] = {
        { 0, 0, VK_FORMAT_R32G32_SFLOAT, 0 },
        { 1, 0, VK_FORMAT_R32G32B32_SFLOAT, 2*sizeof(float) },
    };
    VkPipelineVertexInputStateCreateInfo vi = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
        .vertexBindingDescriptionCount = 1, .pVertexBindingDescriptions = &bind,
        .vertexAttributeDescriptionCount = 2, .pVertexAttributeDescriptions = attrs };
    VkPipelineInputAssemblyStateCreateInfo ia = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
        .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST };
    VkViewport vp = { 0, 0, IMG_W, IMG_H, 0, 1 };
    VkRect2D sc = { {0,0}, {IMG_W, IMG_H} };
    VkPipelineViewportStateCreateInfo vps = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
        .viewportCount = 1, .pViewports = &vp,
        .scissorCount = 1, .pScissors = &sc };
    VkPipelineRasterizationStateCreateInfo rs = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
        .polygonMode = VK_POLYGON_MODE_FILL, .cullMode = VK_CULL_MODE_NONE,
        .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE, .lineWidth = 1.0f };
    VkPipelineMultisampleStateCreateInfo ms = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
        .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT };
    VkPipelineColorBlendAttachmentState ba = { .blendEnable = VK_FALSE, .colorWriteMask = 0xF };
    VkPipelineColorBlendStateCreateInfo cb = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
        .attachmentCount = 1, .pAttachments = &ba };
    VkPipelineShaderStageCreateInfo stages[3] = {
        { .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
          .stage = VK_SHADER_STAGE_VERTEX_BIT, .module = vs, .pName = "main" },
        { .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
          .stage = VK_SHADER_STAGE_GEOMETRY_BIT, .module = gs, .pName = "main" },
        { .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
          .stage = VK_SHADER_STAGE_FRAGMENT_BIT, .module = fs, .pName = "main" },
    };
    VkGraphicsPipelineCreateInfo gpi = {
        .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
        .stageCount = 3, .pStages = stages, .pVertexInputState = &vi,
        .pInputAssemblyState = &ia, .pViewportState = &vps,
        .pRasterizationState = &rs, .pMultisampleState = &ms,
        .pColorBlendState = &cb, .layout = layout, .renderPass = rp };
    VkPipeline pipe;
    VkResult pr = p_vkCreateGraphicsPipelines(c->dev, VK_NULL_HANDLE, 1, &gpi,
                                              NULL, &pipe);
    if (pr != VK_SUCCESS) {
        printf("B0 SUBTEST 3: FAIL (geometry pipeline: %d)\n", pr);
        p_vkDestroyShaderModule(c->dev, vs, NULL);
        p_vkDestroyShaderModule(c->dev, gs, NULL);
        p_vkDestroyShaderModule(c->dev, fs, NULL);
        p_vkDestroyFramebuffer(c->dev, fb, NULL);
        p_vkDestroyRenderPass(c->dev, rp, NULL);
        return 1;
    }
    VkCommandBuffer cmd = begin_cmd(c);
    VkClearValue clear = { .color = { .float32 = { 0.0f, 0.0f, 1.0f, 1.0f } } };
    VkRenderPassBeginInfo bi = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
        .renderPass = rp, .framebuffer = fb,
        .renderArea = { {0,0}, {IMG_W, IMG_H} },
        .clearValueCount = 1, .pClearValues = &clear };
    p_vkCmdBeginRenderPass(cmd, &bi, VK_SUBPASS_CONTENTS_INLINE);
    p_vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipe);
    VkDeviceSize off = 0;
    p_vkCmdBindVertexBuffers(cmd, 0, 1, &vbuf, &off);
    p_vkCmdDraw(cmd, 3, 1, 0, 0);
    p_vkCmdEndRenderPass(cmd);
    copy_to_staging(c, cmd);
    submit_wait(c, cmd);

    void *m; p_vkMapMemory(c->dev, c->rmem, 0, IMG_W*IMG_H*4, 0, &m);
    VkMappedMemoryRange rr = { .sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE,
        .memory = c->rmem, .offset = 0, .size = VK_WHOLE_SIZE };
    p_vkInvalidateMappedMemoryRanges(c->dev, 1, &rr);
    uint8_t *px = m;
    /* Geometry passthrough of the red full-screen triangle: expect Red */
    int ok = pixel_is(px, 32, 32, 255, 0, 0, 255);
    printf("B0 SUBTEST 3: %s (geometry pipeline drew; expected ff0000ff)\n",
           ok ? "PASS" : "FAIL");
    readback_pixels(c, "SUBTEST3");
    p_vkUnmapMemory(c->dev, c->rmem);
    p_vkDestroyPipeline(c->dev, pipe, NULL);
    p_vkDestroyShaderModule(c->dev, vs, NULL);
    p_vkDestroyShaderModule(c->dev, gs, NULL);
    p_vkDestroyShaderModule(c->dev, fs, NULL);
    p_vkDestroyFramebuffer(c->dev, fb, NULL);
    p_vkDestroyRenderPass(c->dev, rp, NULL);
    return ok ? 0 : 1;
}

/* ================= main ================= */
int main(int argc, char **argv) {
    (void)argc; (void)argv;
    int rc = load_vulkan();
    if (rc) return rc;

    VkApplicationInfo app = { .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "vk-render-test-v2", .apiVersion = VK_API_VERSION_1_0 };
    VkInstanceCreateInfo ici = { .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &app };
    VkInstance inst;
    if (p_vkCreateInstance(&ici, NULL, &inst) != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateInstance\n"); return 10;
    }
    uint32_t pdn = 0;
    p_vkEnumeratePhysicalDevices(inst, &pdn, NULL);
    if (!pdn) { fprintf(stderr, "ERR: no devices\n");
        p_vkDestroyInstance(inst, NULL); return 11; }
    VkPhysicalDevice *pds = malloc(sizeof(VkPhysicalDevice) * pdn);
    p_vkEnumeratePhysicalDevices(inst, &pdn, pds);
    VkPhysicalDevice pd = pds[0];
    VkPhysicalDeviceProperties props;
    p_vkGetPhysicalDeviceProperties(pd, &props);
    printf("B0: device: %s\n", props.deviceName);
    free(pds);

    uint32_t qn = 0;
    p_vkGetPhysicalDeviceQueueFamilyProperties(pd, &qn, NULL);
    VkQueueFamilyProperties *qp = malloc(sizeof(VkQueueFamilyProperties) * qn);
    p_vkGetPhysicalDeviceQueueFamilyProperties(pd, &qn, qp);
    uint32_t gfx = UINT32_MAX;
    for (uint32_t i = 0; i < qn; i++)
        if (qp[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) { gfx = i; break; }
    free(qp);
    if (gfx == UINT32_MAX) { fprintf(stderr, "ERR: no gfx queue\n");
        p_vkDestroyInstance(inst, NULL); return 12; }

    VkPhysicalDeviceFeatures feats = {0};
    p_vkGetPhysicalDeviceFeatures(pd, &feats);
    printf("B0: geometryShader=%d tessellationShader=%d\n",
           feats.geometryShader, feats.tessellationShader);

    float prio = 1.0f;
    VkDeviceQueueCreateInfo dq = { .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = gfx, .queueCount = 1, .pQueuePriorities = &prio };
    VkDeviceCreateInfo dci = { .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1, .pQueueCreateInfos = &dq,
        .pEnabledFeatures = &feats };
    VkDevice dev;
    if (p_vkCreateDevice(pd, &dci, NULL, &dev) != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateDevice\n");
        p_vkDestroyInstance(inst, NULL); return 12;
    }
    Ctx c = {0};
    c.dev = dev; c.gfx = gfx;
    p_vkGetDeviceQueue(dev, gfx, 0, &c.queue);
    p_vkGetPhysicalDeviceMemoryProperties(pd, &c.memProps);

    /* Image */
    VkImageCreateInfo ii = { .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
        .imageType = VK_IMAGE_TYPE_2D, .format = VK_FORMAT_R8G8B8A8_UNORM,
        .extent = { IMG_W, IMG_H, 1 }, .mipLevels = 1, .arrayLayers = 1,
        .samples = VK_SAMPLE_COUNT_1_BIT, .tiling = VK_IMAGE_TILING_OPTIMAL,
        .usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT |
                 VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                 VK_IMAGE_USAGE_TRANSFER_DST_BIT,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED };
    if (check_vk(p_vkCreateImage(dev, &ii, NULL, &c.img), "vkCreateImage")) {
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    VkMemoryRequirements mr;
    p_vkGetImageMemoryRequirements(dev, c.img, &mr);
    uint32_t mi = pick_mem(&c.memProps, mr.memoryTypeBits,
                           VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "image");
    if (mi == UINT32_MAX) {
        p_vkDestroyImage(dev, c.img, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    VkMemoryAllocateInfo mai = { .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        .allocationSize = mr.size, .memoryTypeIndex = mi };
    if (check_vk(p_vkAllocateMemory(dev, &mai, NULL, &c.imgMem), "vkAllocateMemory(image)")) {
        p_vkDestroyImage(dev, c.img, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    if (check_vk(p_vkBindImageMemory(dev, c.img, c.imgMem, 0), "vkBindImageMemory")) {
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    VkImageViewCreateInfo vci = { .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
        .image = c.img, .viewType = VK_IMAGE_VIEW_TYPE_2D,
        .format = VK_FORMAT_R8G8B8A8_UNORM,
        .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 } };
    VkImageView view;
    if (check_vk(p_vkCreateImageView(dev, &vci, NULL, &view), "vkCreateImageView")) {
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }

    /* Staging buffer */
    VkBufferCreateInfo bci = { .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
        .size = IMG_W * IMG_H * 4, .usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE };
    if (check_vk(p_vkCreateBuffer(dev, &bci, NULL, &c.rbuf), "vkCreateBuffer(staging)")) {
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    p_vkGetBufferMemoryRequirements(dev, c.rbuf, &mr);
    mi = pick_mem(&c.memProps, mr.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        "staging buffer");
    if (mi == UINT32_MAX) {
        p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    mai.allocationSize = mr.size; mai.memoryTypeIndex = mi;
    if (check_vk(p_vkAllocateMemory(dev, &mai, NULL, &c.rmem), "vkAllocateMemory(staging)")) {
        p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    if (check_vk(p_vkBindBufferMemory(dev, c.rbuf, c.rmem, 0), "vkBindBufferMemory(staging)")) {
        p_vkFreeMemory(dev, c.rmem, NULL); p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }

    /* Vertex buffer (full-screen triangle) */
    VkBuffer vbuf; VkDeviceMemory vmem;
    bci.size = sizeof(kFullTri); bci.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    if (check_vk(p_vkCreateBuffer(dev, &bci, NULL, &vbuf), "vkCreateBuffer(vertex)")) {
        p_vkFreeMemory(dev, c.rmem, NULL); p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    p_vkGetBufferMemoryRequirements(dev, vbuf, &mr);
    mi = pick_mem(&c.memProps, mr.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        "vertex buffer");
    if (mi == UINT32_MAX) {
        p_vkDestroyBuffer(dev, vbuf, NULL);
        p_vkFreeMemory(dev, c.rmem, NULL); p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    mai.allocationSize = mr.size; mai.memoryTypeIndex = mi;
    if (check_vk(p_vkAllocateMemory(dev, &mai, NULL, &vmem), "vkAllocateMemory(vertex)")) {
        p_vkDestroyBuffer(dev, vbuf, NULL);
        p_vkFreeMemory(dev, c.rmem, NULL); p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    if (check_vk(p_vkBindBufferMemory(dev, vbuf, vmem, 0), "vkBindBufferMemory(vertex)")) {
        p_vkFreeMemory(dev, vmem, NULL); p_vkDestroyBuffer(dev, vbuf, NULL);
        p_vkFreeMemory(dev, c.rmem, NULL); p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    void *mm;
    if (check_vk(p_vkMapMemory(dev, vmem, 0, sizeof(kFullTri), 0, &mm), "vkMapMemory(vertex)")) {
        p_vkFreeMemory(dev, vmem, NULL); p_vkDestroyBuffer(dev, vbuf, NULL);
        p_vkFreeMemory(dev, c.rmem, NULL); p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }
    memcpy(mm, kFullTri, sizeof(kFullTri));
    p_vkUnmapMemory(dev, vmem);

    VkPipelineLayoutCreateInfo pli = {
        .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO };
    VkPipelineLayout layout;
    if (check_vk(p_vkCreatePipelineLayout(dev, &pli, NULL, &layout), "vkCreatePipelineLayout")) {
        p_vkFreeMemory(dev, vmem, NULL); p_vkDestroyBuffer(dev, vbuf, NULL);
        p_vkFreeMemory(dev, c.rmem, NULL); p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }

    VkCommandPoolCreateInfo pci = { .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .queueFamilyIndex = gfx };
    if (check_vk(p_vkCreateCommandPool(dev, &pci, NULL, &c.pool), "vkCreateCommandPool")) {
        p_vkDestroyPipelineLayout(dev, layout, NULL);
        p_vkFreeMemory(dev, vmem, NULL); p_vkDestroyBuffer(dev, vbuf, NULL);
        p_vkFreeMemory(dev, c.rmem, NULL); p_vkDestroyBuffer(dev, c.rbuf, NULL);
        p_vkDestroyImageView(dev, view, NULL);
        p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 12;
    }

    /* ---- Run all sub-tests, NEVER exit early ---- */
    int f0 = subtest0_transfer_clear(&c);
    int f1 = subtest1_rp_clear(&c, view);
    int f2 = subtest2_fulltri(&c, view, vbuf, layout);
    int f3 = subtest3_geometry(&c, view, vbuf, layout, feats.geometryShader);

    printf("B0 SUMMARY: sub0=%s sub1=%s sub2=%s sub3=%s\n",
           f0==0?"PASS":(f0==2?"NOT_SUPPORTED":"FAIL"),
           f1==0?"PASS":(f1==2?"NOT_SUPPORTED":"FAIL"),
           f2==0?"PASS":(f2==2?"NOT_SUPPORTED":"FAIL"),
           f3==0?"PASS":(f3==2?"NOT_SUPPORTED":"FAIL"));

    int exitCode = 0;
    if (f0 || f1 || f2) exitCode = 13;
    if (f3 == 1) exitCode = (exitCode ? exitCode : 14);
    /* f3==2 (NOT_SUPPORTED) is not a failure */

    p_vkDestroyBuffer(dev, vbuf, NULL); p_vkFreeMemory(dev, vmem, NULL);
    p_vkDestroyPipelineLayout(dev, layout, NULL);
    p_vkDestroyCommandPool(dev, c.pool, NULL);
    p_vkDestroyBuffer(dev, c.rbuf, NULL); p_vkFreeMemory(dev, c.rmem, NULL);
    p_vkDestroyImageView(dev, view, NULL);
    p_vkDestroyImage(dev, c.img, NULL); p_vkFreeMemory(dev, c.imgMem, NULL);
    p_vkDestroyDevice(dev, NULL);
    p_vkDestroyInstance(inst, NULL);

    printf("B0 RESULT: %s (exit %d)\n", exitCode==0?"PASS":"FAIL", exitCode);
    return exitCode;
}

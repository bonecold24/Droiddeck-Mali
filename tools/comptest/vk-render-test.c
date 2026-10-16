/*
 * vk-render-test.c — headless Vulkan graphics smoke test (DroidDeck Mali Tier 0, Stage B0).
 *
 * See header comment in previous version for full documentation.
 * This file implements the complete offscreen render pipeline.
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

/* Vulkan function pointers */
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
VK_FN(vkCreateCommandPool); VK_FN(vkDestroyCommandPool);
VK_FN(vkAllocateCommandBuffers); VK_FN(vkFreeCommandBuffers);
VK_FN(vkBeginCommandBuffer); VK_FN(vkEndCommandBuffer);
VK_FN(vkCmdBeginRenderPass); VK_FN(vkCmdEndRenderPass); VK_FN(vkCmdBindPipeline);
VK_FN(vkCmdBindVertexBuffers); VK_FN(vkCmdDraw); VK_FN(vkCmdPipelineBarrier);
VK_FN(vkCmdCopyImageToBuffer); VK_FN(vkQueueSubmit); VK_FN(vkQueueWaitIdle);
VK_FN(vkCreateImageView); VK_FN(vkDestroyImageView);
#undef VK_FN

static int load_vulkan(void) {
    g_vk = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!g_vk) g_vk = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!g_vk) { fprintf(stderr, "ERR: dlopen libvulkan failed: %s\n", dlerror()); return 9; }
#define LOAD(n) do { p_##n = (PFN_##n)dlsym(g_vk, #n); if (!p_##n) return 9; } while(0)
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
    LOAD(vkCreateCommandPool); LOAD(vkDestroyCommandPool); LOAD(vkAllocateCommandBuffers);
    LOAD(vkFreeCommandBuffers); LOAD(vkBeginCommandBuffer); LOAD(vkEndCommandBuffer);
    LOAD(vkCmdBeginRenderPass); LOAD(vkCmdEndRenderPass); LOAD(vkCmdBindPipeline);
    LOAD(vkCmdBindVertexBuffers); LOAD(vkCmdDraw); LOAD(vkCmdPipelineBarrier);
    LOAD(vkCmdCopyImageToBuffer); LOAD(vkQueueSubmit); LOAD(vkQueueWaitIdle);
    LOAD(vkCreateImageView); LOAD(vkDestroyImageView);
#undef LOAD
    return 0;
}

/* Triangle vertices: pos(x,y), color(r,g,b) */
static const float kVertices[] = {
    -0.5f, -0.5f,  1.0f, 0.0f, 0.0f,  /* bottom-left, red */
     0.5f, -0.5f,  0.0f, 1.0f, 0.0f,  /* bottom-right, green */
     0.0f,  0.5f,  0.0f, 0.0f, 1.0f,  /* top, blue */
};

static uint32_t find_mem_type(VkPhysicalDeviceMemoryProperties *mp, uint32_t bits, VkMemoryPropertyFlags flags) {
    for (uint32_t i = 0; i < mp->memoryTypeCount; i++) {
        if ((bits & (1u << i)) && (mp->memoryTypes[i].propertyFlags & flags) == flags) return i;
    }
    return UINT32_MAX;
}

int main(int argc, char **argv) {
    (void)argc; (void)argv;
    int rc = load_vulkan();
    if (rc) return rc;

    /* Instance */
    VkApplicationInfo app = { .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "vk-render-test", .apiVersion = VK_API_VERSION_1_0 };
    VkInstanceCreateInfo ici = { .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &app };
    VkInstance inst;
    if (p_vkCreateInstance(&ici, NULL, &inst) != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateInstance failed\n"); return 10;
    }

    /* Physical device */
    uint32_t pdCount = 0;
    p_vkEnumeratePhysicalDevices(inst, &pdCount, NULL);
    if (pdCount == 0) {
        fprintf(stderr, "ERR: no physical devices\n");
        p_vkDestroyInstance(inst, NULL); return 11;
    }
    VkPhysicalDevice *pds = malloc(sizeof(VkPhysicalDevice) * pdCount);
    p_vkEnumeratePhysicalDevices(inst, &pdCount, pds);
    VkPhysicalDevice pd = pds[0];
    VkPhysicalDeviceProperties props;
    p_vkGetPhysicalDeviceProperties(pd, &props);
    printf("device: %s\n", props.deviceName);
    free(pds);

    /* Graphics queue */
    uint32_t qCount = 0;
    p_vkGetPhysicalDeviceQueueFamilyProperties(pd, &qCount, NULL);
    VkQueueFamilyProperties *qps = malloc(sizeof(VkQueueFamilyProperties) * qCount);
    p_vkGetPhysicalDeviceQueueFamilyProperties(pd, &qCount, qps);
    uint32_t gfx = UINT32_MAX;
    for (uint32_t i = 0; i < qCount; i++)
        if (qps[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) { gfx = i; break; }
    free(qps);
    if (gfx == UINT32_MAX) {
        fprintf(stderr, "ERR: no graphics queue\n");
        p_vkDestroyInstance(inst, NULL); return 12;
    }

    /* Features */
    VkPhysicalDeviceFeatures feats = {0};
    p_vkGetPhysicalDeviceFeatures(pd, &feats);
    printf("B0: geometryShader=%s tessellationShader=%s\n",
           feats.geometryShader ? "PRESENT" : "NOT_SUPPORTED",
           feats.tessellationShader ? "PRESENT" : "NOT_SUPPORTED");

    /* Logical device */
    float prio = 1.0f;
    VkDeviceQueueCreateInfo dqci = { .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = gfx, .queueCount = 1, .pQueuePriorities = &prio };
    VkDeviceCreateInfo dci = { .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1, .pQueueCreateInfos = &dqci, .pEnabledFeatures = &feats };
    VkDevice dev;
    if (p_vkCreateDevice(pd, &dci, NULL, &dev) != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateDevice failed\n");
        p_vkDestroyInstance(inst, NULL); return 12;
    }
    VkQueue queue;
    p_vkGetDeviceQueue(dev, gfx, 0, &queue);

    VkPhysicalDeviceMemoryProperties memProps;
    p_vkGetPhysicalDeviceMemoryProperties(pd, &memProps);

    /* --- Offscreen image --- */
    VkImageCreateInfo ici2 = { .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
        .imageType = VK_IMAGE_TYPE_2D, .format = VK_FORMAT_R8G8B8A8_UNORM,
        .extent = { IMG_W, IMG_H, 1 }, .mipLevels = 1, .arrayLayers = 1,
        .samples = VK_SAMPLE_COUNT_1_BIT, .tiling = VK_IMAGE_TILING_OPTIMAL,
        .usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED };
    VkImage img;
    if (p_vkCreateImage(dev, &ici2, NULL, &img) != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateImage failed\n");
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 13;
    }
    VkMemoryRequirements mr;
    p_vkGetImageMemoryRequirements(dev, img, &mr);
    uint32_t memIdx = find_mem_type(&memProps, mr.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (memIdx == UINT32_MAX) memIdx = find_mem_type(&memProps, mr.memoryTypeBits, 0);
    VkMemoryAllocateInfo mai = { .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        .allocationSize = mr.size, .memoryTypeIndex = memIdx };
    VkDeviceMemory imgMem;
    p_vkAllocateMemory(dev, &mai, NULL, &imgMem);
    p_vkBindImageMemory(dev, img, imgMem, 0);

    VkImageViewCreateInfo ivci = { .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
        .image = img, .viewType = VK_IMAGE_VIEW_TYPE_2D, .format = VK_FORMAT_R8G8B8A8_UNORM,
        .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 } };
    VkImageView view;
    p_vkCreateImageView(dev, &ivci, NULL, &view);

    /* --- Render pass --- */
    VkAttachmentDescription att = { .format = VK_FORMAT_R8G8B8A8_UNORM,
        .samples = VK_SAMPLE_COUNT_1_BIT, .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
        .storeOp = VK_ATTACHMENT_STORE_OP_STORE, .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
        .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .finalLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL };
    VkAttachmentReference ref = { .attachment = 0, .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL };
    VkSubpassDescription sub = { .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
        .colorAttachmentCount = 1, .pColorAttachments = &ref };
    VkRenderPassCreateInfo rpci = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
        .attachmentCount = 1, .pAttachments = &att, .subpassCount = 1, .pSubpasses = &sub };
    VkRenderPass rp;
    p_vkCreateRenderPass(dev, &rpci, NULL, &rp);

    VkFramebufferCreateInfo fbci = { .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
        .renderPass = rp, .attachmentCount = 1, .pAttachments = &view,
        .width = IMG_W, .height = IMG_H, .layers = 1 };
    VkFramebuffer fb;
    p_vkCreateFramebuffer(dev, &fbci, NULL, &fb);

    /* --- Vertex buffer --- */
    VkBufferCreateInfo bci = { .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
        .size = sizeof(kVertices), .usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE };
    VkBuffer vbuf;
    p_vkCreateBuffer(dev, &bci, NULL, &vbuf);
    p_vkGetBufferMemoryRequirements(dev, vbuf, &mr);
    memIdx = find_mem_type(&memProps, mr.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    mai.allocationSize = mr.size; mai.memoryTypeIndex = memIdx;
    VkDeviceMemory vmem;
    p_vkAllocateMemory(dev, &mai, NULL, &vmem);
    p_vkBindBufferMemory(dev, vbuf, vmem, 0);
    void *mapped;
    p_vkMapMemory(dev, vmem, 0, sizeof(kVertices), 0, &mapped);
    memcpy(mapped, kVertices, sizeof(kVertices));
    p_vkUnmapMemory(dev, vmem);

    /* --- Shaders --- */
    VkShaderModuleCreateInfo smci = { .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO };
    VkShaderModule vertMod, fragMod;
    smci.codeSize = sizeof(kTriangleVertSpv); smci.pCode = kTriangleVertSpv;
    p_vkCreateShaderModule(dev, &smci, NULL, &vertMod);
    smci.codeSize = sizeof(kTriangleFragSpv); smci.pCode = kTriangleFragSpv;
    p_vkCreateShaderModule(dev, &smci, NULL, &fragMod);

    /* --- Pipeline --- */
    VkPipelineLayoutCreateInfo plci = { .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO };
    VkPipelineLayout layout;
    p_vkCreatePipelineLayout(dev, &plci, NULL, &layout);

    VkVertexInputBindingDescription bind = { .binding = 0, .stride = 5 * sizeof(float),
        .inputRate = VK_VERTEX_INPUT_RATE_VERTEX };
    VkVertexInputAttributeDescription attrs[2] = {
        { .location = 0, .binding = 0, .format = VK_FORMAT_R32G32_SFLOAT, .offset = 0 },
        { .location = 1, .binding = 0, .format = VK_FORMAT_R32G32B32_SFLOAT, .offset = 2 * sizeof(float) },
    };
    VkPipelineVertexInputStateCreateInfo vi = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
        .vertexBindingDescriptionCount = 1, .pVertexBindingDescriptions = &bind,
        .vertexAttributeDescriptionCount = 2, .pVertexAttributeDescriptions = attrs };
    VkPipelineInputAssemblyStateCreateInfo ia = { .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
        .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST };
    VkViewport vp = { .x = 0, .y = 0, .width = IMG_W, .height = IMG_H, .minDepth = 0, .maxDepth = 1 };
    VkRect2D sc = { .offset = {0, 0}, .extent = {IMG_W, IMG_H} };
    VkPipelineViewportStateCreateInfo vps = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
        .viewportCount = 1, .pViewports = &vp, .scissorCount = 1, .pScissors = &sc };
    VkPipelineRasterizationStateCreateInfo rs = { .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
        .polygonMode = VK_POLYGON_MODE_FILL, .cullMode = VK_CULL_MODE_NONE,
        .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE, .lineWidth = 1.0f };
    VkPipelineMultisampleStateCreateInfo ms = { .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
        .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT };
    VkPipelineColorBlendAttachmentState ba = { .blendEnable = VK_FALSE,
        .colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                          VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT };
    VkPipelineColorBlendStateCreateInfo cb = { .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
        .attachmentCount = 1, .pAttachments = &ba };
    VkPipelineShaderStageCreateInfo stages[2] = {
        { .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
          .stage = VK_SHADER_STAGE_VERTEX_BIT, .module = vertMod, .pName = "main" },
        { .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
          .stage = VK_SHADER_STAGE_FRAGMENT_BIT, .module = fragMod, .pName = "main" },
    };
    VkGraphicsPipelineCreateInfo gpci = { .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
        .stageCount = 2, .pStages = stages, .pVertexInputState = &vi,
        .pInputAssemblyState = &ia, .pViewportState = &vps, .pRasterizationState = &rs,
        .pMultisampleState = &ms, .pColorBlendState = &cb,
        .layout = layout, .renderPass = rp, .subpass = 0 };
    VkPipeline pipe;
    if (p_vkCreateGraphicsPipelines(dev, VK_NULL_HANDLE, 1, &gpci, NULL, &pipe) != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateGraphicsPipelines failed\n");
        p_vkDestroyDevice(dev, NULL); p_vkDestroyInstance(inst, NULL); return 13;
    }

    /* --- Readback buffer --- */
    VkBufferCreateInfo rbci = { .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
        .size = IMG_W * IMG_H * 4, .usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE };
    VkBuffer rbuf;
    p_vkCreateBuffer(dev, &rbci, NULL, &rbuf);
    p_vkGetBufferMemoryRequirements(dev, rbuf, &mr);
    memIdx = find_mem_type(&memProps, mr.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    mai.allocationSize = mr.size; mai.memoryTypeIndex = memIdx;
    VkDeviceMemory rmem;
    p_vkAllocateMemory(dev, &mai, NULL, &rmem);
    p_vkBindBufferMemory(dev, rbuf, rmem, 0);

    /* --- Command buffer --- */
    VkCommandPoolCreateInfo cpci = { .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .queueFamilyIndex = gfx };
    VkCommandPool pool;
    p_vkCreateCommandPool(dev, &cpci, NULL, &pool);
    VkCommandBufferAllocateInfo cbai = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = pool, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1 };
    VkCommandBuffer cmd;
    p_vkAllocateCommandBuffers(dev, &cbai, &cmd);

    VkCommandBufferBeginInfo bbi = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT };
    p_vkBeginCommandBuffer(cmd, &bbi);
    VkClearValue clear = { .color = { .float32 = { 0.0f, 0.0f, 0.0f, 1.0f } } };
    VkRenderPassBeginInfo rpbi = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
        .renderPass = rp, .framebuffer = fb, .renderArea = { {0,0}, {IMG_W, IMG_H} },
        .clearValueCount = 1, .pClearValues = &clear };
    p_vkCmdBeginRenderPass(cmd, &rpbi, VK_SUBPASS_CONTENTS_INLINE);
    p_vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipe);
    VkDeviceSize off = 0;
    p_vkCmdBindVertexBuffers(cmd, 0, 1, &vbuf, &off);
    p_vkCmdDraw(cmd, 3, 1, 0, 0);
    p_vkCmdEndRenderPass(cmd);
    /* Barrier: COLOR_ATTACHMENT_OPTIMAL -> TRANSFER_SRC_OPTIMAL (already done by render pass) */
    VkBufferImageCopy region = { .bufferOffset = 0, .bufferRowLength = 0, .bufferImageHeight = 0,
        .imageSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 },
        .imageOffset = { 0, 0, 0 }, .imageExtent = { IMG_W, IMG_H, 1 } };
    p_vkCmdCopyImageToBuffer(cmd, img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, rbuf, 1, &region);
    p_vkEndCommandBuffer(cmd);

    VkSubmitInfo si = { .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .commandBufferCount = 1, .pCommandBuffers = &cmd };
    p_vkQueueSubmit(queue, 1, &si, VK_NULL_HANDLE);
    p_vkQueueWaitIdle(queue);

    /* --- Read back and verify --- */
    p_vkMapMemory(dev, rmem, 0, IMG_W * IMG_H * 4, 0, &mapped);
    uint8_t *px = (uint8_t *)mapped;

    /* Sample points: (x,y) with y=0 at top (Vulkan image layout) */
    struct { uint32_t x, y; const char *desc; int expectNonBlack; } samples[] = {
        { 32, 32, "center (inside triangle)", 1 },
        { 5, 5, "corner (outside triangle)", 0 },
        { 32, 20, "edge-adjacent (inside, near top)", 1 },
        { 10, 50, "bottom-left (outside)", 0 },
    };
    int failures = 0;
    printf("B0: pixel samples (RGBA):\n");
    for (size_t i = 0; i < sizeof(samples)/sizeof(samples[0]); i++) {
        uint32_t x = samples[i].x, y = samples[i].y;
        uint8_t r = px[(y * IMG_W + x) * 4 + 0];
        uint8_t g = px[(y * IMG_W + x) * 4 + 1];
        uint8_t b = px[(y * IMG_W + x) * 4 + 2];
        uint8_t a = px[(y * IMG_W + x) * 4 + 3];
        int nonBlack = (r > 10 || g > 10 || b > 10);
        int ok = (nonBlack == samples[i].expectNonBlack);
        printf("B0:   (%u,%u) %s: RGBA=(%u,%u,%u,%u) %s\n",
               x, y, samples[i].desc, r, g, b, a, ok ? "OK" : "MISMATCH");
        if (!ok) failures++;
    }
    p_vkUnmapMemory(dev, rmem);

    /* Cleanup Test 1 */
    p_vkDestroyPipeline(dev, pipe, NULL);
    p_vkDestroyShaderModule(dev, vertMod, NULL);
    p_vkDestroyShaderModule(dev, fragMod, NULL);

    int exitCode = 0;
    if (failures > 0) {
        fprintf(stderr, "ERR: Test 1 (triangle) FAILED: %d pixel mismatches\n", failures);
        exitCode = 13;
    } else {
        printf("B0: Test 1 (triangle) PASS\n");
    }

    /* --- Test 2: Geometry shader --- */
    if (feats.geometryShader) {
        VkShaderModule geomMod;
        smci.codeSize = sizeof(kPassthroughGeomSpv); smci.pCode = kPassthroughGeomSpv;
        if (p_vkCreateShaderModule(dev, &smci, NULL, &geomMod) != VK_SUCCESS) {
            fprintf(stderr, "ERR: geometry shader module creation failed\n");
            exitCode = 14;
        } else {
            printf("B0: Test 2 (geometry) PASS (module created)\n");
            p_vkDestroyShaderModule(dev, geomMod, NULL);
        }
    } else {
        printf("B0: Test 2 (geometry) NOT_SUPPORTED\n");
    }

    /* --- Test 3: Tessellation --- */
    if (feats.tessellationShader) {
        VkShaderModule tescMod, teseMod;
        smci.codeSize = sizeof(kPassthroughTescSpv); smci.pCode = kPassthroughTescSpv;
        VkResult r1 = p_vkCreateShaderModule(dev, &smci, NULL, &tescMod);
        smci.codeSize = sizeof(kPassthroughTeseSpv); smci.pCode = kPassthroughTeseSpv;
        VkResult r2 = p_vkCreateShaderModule(dev, &smci, NULL, &teseMod);
        if (r1 != VK_SUCCESS || r2 != VK_SUCCESS) {
            fprintf(stderr, "ERR: tessellation shader module creation failed\n");
            exitCode = 15;
        } else {
            printf("B0: Test 3 (tessellation) PASS (modules created)\n");
            p_vkDestroyShaderModule(dev, tescMod, NULL);
            p_vkDestroyShaderModule(dev, teseMod, NULL);
        }
    } else {
        printf("B0: Test 3 (tessellation) NOT_SUPPORTED\n");
    }

    /* Cleanup */
    p_vkFreeCommandBuffers(dev, pool, 1, &cmd);
    p_vkDestroyCommandPool(dev, pool, NULL);
    p_vkDestroyBuffer(dev, rbuf, NULL); p_vkFreeMemory(dev, rmem, NULL);
    p_vkDestroyBuffer(dev, vbuf, NULL); p_vkFreeMemory(dev, vmem, NULL);
    p_vkDestroyPipelineLayout(dev, layout, NULL);
    p_vkDestroyFramebuffer(dev, fb, NULL);
    p_vkDestroyRenderPass(dev, rp, NULL);
    p_vkDestroyImageView(dev, view, NULL);
    p_vkDestroyImage(dev, img, NULL); p_vkFreeMemory(dev, imgMem, NULL);
    p_vkDestroyDevice(dev, NULL);
    p_vkDestroyInstance(inst, NULL);

    if (exitCode == 0) printf("B0 RESULT: PASS\n");
    else printf("B0 RESULT: FAIL (exit %d)\n", exitCode);
    return exitCode;
}

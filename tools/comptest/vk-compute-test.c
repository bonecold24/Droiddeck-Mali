/*
 * vk-compute-test.c — headless Vulkan compute smoke test (DroidDeck Mali Tier 0).
 *
 * Purpose: separate GPU bring-up from display bring-up. Needs NO display
 * server: pure compute. Creates a VkInstance, enumerates physical devices
 * (printing deviceName/driverVersion/apiVersion), creates a logical device,
 * runs a trivial vector-add compute shader, reads the result back and
 * verifies it.
 *
 * Exit codes (distinct per failing step):
 *    0  PASS — compute result verified
 *    9  Vulkan loader (libvulkan.so.1) could not be dlopen()ed
 *   10  vkCreateInstance failed (or instance entry points missing)
 *   11  no physical devices enumerated
 *   12  vkCreateDevice failed (or no compute queue family)
 *   13  compute failed (shader / pipeline / buffers / dispatch / verify)
 *
 * Build (CI, aarch64 glibc):
 *   aarch64-linux-gnu-gcc -O2 -Wall -o vk-compute-test \
 *       tools/comptest/vk-compute-test.c -ldl
 * Vulkan is loaded at runtime via dlopen(); only the headers are needed
 * at build time (libvulkan-dev). No link-time -lvulkan dependency.
 *
 * License: MIT (test utility for the DroidDeck Mali mission).
 */

#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>

#define ELEM_COUNT 256u
#define LOCAL_SIZE 64u

/*
 * Hand-assembled SPIR-V 1.0 for:
 *   #version 450
 *   layout(local_size_x = 64) in;
 *   layout(set = 0, binding = 0) buffer A { float a[]; };
 *   layout(set = 0, binding = 1) buffer B { float b[]; };
 *   layout(set = 0, binding = 2) buffer C { float c[]; };
 *   void main() {
 *       uint i = gl_GlobalInvocationID.x;
 *       c[i] = a[i] + b[i];
 *   }
 *
 * IDs: 1=extinst 2=void 3=fntype 4=uint 5=v3uint 6=ptrInV3 7=gl_GlobalInvocationID
 * 8=float 9=rarrFloat 10/13/16=SSBO structs 11/14/17=ptrSSBO 12/15/18=vars A/B/C
 * 19=ptrSSBOFloat 20=const0u 21=main 22=label 23..30=temporaries. Bound=31.
 */
static const uint32_t kShaderSpv[] = {
    /* header: magic, version 1.0, generator 0, bound 31, schema 0 */
    0x07230203u, 0x00010000u, 0x00000000u, 31u, 0x00000000u,
    /* OpCapability Shader */
    0x00020011u, 0x00000001u,
    /* OpExtInstImport "GLSL.std.450" */
    0x0006000Bu, 0x00000001u, 0x4C534C47u, 0x6474732Eu, 0x3035342Eu, 0x00000000u,
    /* OpMemoryModel Logical GLSL450 */
    0x0003000Eu, 0x00000000u, 0x00000001u,
    /* OpEntryPoint GLCompute %21 "main" %7 */
    0x0006000Fu, 0x00000005u, 0x00000015u, 0x6E69616Du, 0x00000000u, 0x00000007u,
    /* OpExecutionMode %21 LocalSize 64 1 1 */
    0x00060010u, 0x00000015u, 0x00000011u, 0x00000040u, 0x00000001u, 0x00000001u,
    /* OpSource GLSL 450 */
    0x00030003u, 0x00000001u, 0x000001C2u,
    /* OpDecorate %7 BuiltIn GlobalInvocationId */
    0x00040047u, 0x00000007u, 0x0000000Bu, 0x0000001Cu,
    /* OpDecorate %9 ArrayStride 4 */
    0x00040047u, 0x00000009u, 0x00000006u, 0x00000004u,
    /* OpDecorate %12 DescriptorSet 0 / Binding 0 */
    0x00040047u, 0x0000000Cu, 0x00000022u, 0x00000000u,
    0x00040047u, 0x0000000Cu, 0x00000021u, 0x00000000u,
    /* OpDecorate %15 DescriptorSet 0 / Binding 1 */
    0x00040047u, 0x0000000Fu, 0x00000022u, 0x00000000u,
    0x00040047u, 0x0000000Fu, 0x00000021u, 0x00000001u,
    /* OpDecorate %18 DescriptorSet 0 / Binding 2 */
    0x00040047u, 0x00000012u, 0x00000022u, 0x00000000u,
    0x00040047u, 0x00000012u, 0x00000021u, 0x00000002u,
    /* OpDecorate %10 %13 %16 Block */
    0x00030047u, 0x0000000Au, 0x00000002u,
    0x00030047u, 0x0000000Du, 0x00000002u,
    0x00030047u, 0x00000010u, 0x00000002u,
    /* OpMemberDecorate %10/%13/%16 0 Offset 0 */
    0x00050048u, 0x0000000Au, 0x00000000u, 0x00000023u, 0x00000000u,
    0x00050048u, 0x0000000Du, 0x00000000u, 0x00000023u, 0x00000000u,
    0x00050048u, 0x00000010u, 0x00000000u, 0x00000023u, 0x00000000u,
    /* %2 = OpTypeVoid */
    0x00020013u, 0x00000002u,
    /* %3 = OpTypeFunction %2 */
    0x00030021u, 0x00000003u, 0x00000002u,
    /* %4 = OpTypeInt 32 0 */
    0x00040015u, 0x00000004u, 0x00000020u, 0x00000000u,
    /* %5 = OpTypeVector %4 3 */
    0x00040017u, 0x00000005u, 0x00000004u, 0x00000003u,
    /* %6 = OpTypePointer Input %5 */
    0x00040020u, 0x00000006u, 0x00000001u, 0x00000005u,
    /* %7 = OpVariable %6 Input */
    0x0004003Bu, 0x00000006u, 0x00000007u, 0x00000001u,
    /* %8 = OpTypeFloat 32 */
    0x00030016u, 0x00000008u, 0x00000020u,
    /* %9 = OpTypeRuntimeArray %8 */
    0x0003001Du, 0x00000009u, 0x00000008u,
    /* %10 = OpTypeStruct %9 */
    0x0003001Eu, 0x0000000Au, 0x00000009u,
    /* %11 = OpTypePointer StorageBuffer %10 */
    0x00040020u, 0x0000000Bu, 0x0000000Cu, 0x0000000Au,
    /* %12 = OpVariable %11 StorageBuffer */
    0x0004003Bu, 0x0000000Bu, 0x0000000Cu, 0x0000000Cu,
    /* %13 = OpTypeStruct %9 */
    0x0003001Eu, 0x0000000Du, 0x00000009u,
    /* %14 = OpTypePointer StorageBuffer %13 */
    0x00040020u, 0x0000000Eu, 0x0000000Cu, 0x0000000Du,
    /* %15 = OpVariable %14 StorageBuffer */
    0x0004003Bu, 0x0000000Eu, 0x0000000Fu, 0x0000000Cu,
    /* %16 = OpTypeStruct %9 */
    0x0003001Eu, 0x00000010u, 0x00000009u,
    /* %17 = OpTypePointer StorageBuffer %16 */
    0x00040020u, 0x00000011u, 0x0000000Cu, 0x00000010u,
    /* %18 = OpVariable %17 StorageBuffer */
    0x0004003Bu, 0x00000011u, 0x00000012u, 0x0000000Cu,
    /* %19 = OpTypePointer StorageBuffer %8 */
    0x00040020u, 0x00000013u, 0x0000000Cu, 0x00000008u,
    /* %20 = OpConstant %4 0 */
    0x0004002Bu, 0x00000004u, 0x00000014u, 0x00000000u,
    /* %21 = OpFunction %2 None %3 */
    0x00050036u, 0x00000002u, 0x00000015u, 0x00000000u, 0x00000003u,
    /* %22 = OpLabel */
    0x000200F8u, 0x00000016u,
    /* %23 = OpLoad %5 %7 */
    0x0004003Du, 0x00000005u, 0x00000017u, 0x00000007u,
    /* %24 = OpCompositeExtract %4 %23 0 */
    0x00050051u, 0x00000004u, 0x00000018u, 0x00000017u, 0x00000000u,
    /* %25 = OpAccessChain %19 %12 %20 %24 */
    0x00060041u, 0x00000013u, 0x00000019u, 0x0000000Cu, 0x00000014u, 0x00000018u,
    /* %26 = OpLoad %8 %25 */
    0x0004003Du, 0x00000008u, 0x0000001Au, 0x00000019u,
    /* %27 = OpAccessChain %19 %15 %20 %24 */
    0x00060041u, 0x00000013u, 0x0000001Bu, 0x0000000Fu, 0x00000014u, 0x00000018u,
    /* %28 = OpLoad %8 %27 */
    0x0004003Du, 0x00000008u, 0x0000001Cu, 0x0000001Bu,
    /* %29 = OpFAdd %8 %26 %28 */
    0x00050081u, 0x00000008u, 0x0000001Du, 0x0000001Au, 0x0000001Cu,
    /* %30 = OpAccessChain %19 %18 %20 %24 */
    0x00060041u, 0x00000013u, 0x0000001Eu, 0x00000012u, 0x00000014u, 0x00000018u,
    /* OpStore %30 %29 */
    0x0003003Eu, 0x0000001Eu, 0x0000001Du,
    /* OpReturn */
    0x000100FDu,
    /* OpFunctionEnd */
    0x00010038u,
};

/* ---- runtime-loaded Vulkan entry points ---- */

static PFN_vkGetInstanceProcAddr g_gipa;

/* instance-level */
static PFN_vkCreateInstance p_vkCreateInstance;
static PFN_vkDestroyInstance p_vkDestroyInstance;
static PFN_vkEnumeratePhysicalDevices p_vkEnumeratePhysicalDevices;
static PFN_vkGetPhysicalDeviceProperties p_vkGetPhysicalDeviceProperties;
static PFN_vkGetPhysicalDeviceQueueFamilyProperties p_vkGetPhysicalDeviceQueueFamilyProperties;
static PFN_vkGetPhysicalDeviceMemoryProperties p_vkGetPhysicalDeviceMemoryProperties;
static PFN_vkCreateDevice p_vkCreateDevice;
static PFN_vkDestroyDevice p_vkDestroyDevice;
static PFN_vkGetDeviceProcAddr p_vkGetDeviceProcAddr;

/* device-level */
static PFN_vkGetDeviceQueue p_vkGetDeviceQueue;
static PFN_vkCreateBuffer p_vkCreateBuffer;
static PFN_vkDestroyBuffer p_vkDestroyBuffer;
static PFN_vkGetBufferMemoryRequirements p_vkGetBufferMemoryRequirements;
static PFN_vkAllocateMemory p_vkAllocateMemory;
static PFN_vkFreeMemory p_vkFreeMemory;
static PFN_vkBindBufferMemory p_vkBindBufferMemory;
static PFN_vkMapMemory p_vkMapMemory;
static PFN_vkUnmapMemory p_vkUnmapMemory;
static PFN_vkCreateShaderModule p_vkCreateShaderModule;
static PFN_vkDestroyShaderModule p_vkDestroyShaderModule;
static PFN_vkCreateDescriptorSetLayout p_vkCreateDescriptorSetLayout;
static PFN_vkDestroyDescriptorSetLayout p_vkDestroyDescriptorSetLayout;
static PFN_vkCreatePipelineLayout p_vkCreatePipelineLayout;
static PFN_vkDestroyPipelineLayout p_vkDestroyPipelineLayout;
static PFN_vkCreateComputePipelines p_vkCreateComputePipelines;
static PFN_vkDestroyPipeline p_vkDestroyPipeline;
static PFN_vkCreateDescriptorPool p_vkCreateDescriptorPool;
static PFN_vkDestroyDescriptorPool p_vkDestroyDescriptorPool;
static PFN_vkAllocateDescriptorSets p_vkAllocateDescriptorSets;
static PFN_vkUpdateDescriptorSets p_vkUpdateDescriptorSets;
static PFN_vkCreateCommandPool p_vkCreateCommandPool;
static PFN_vkDestroyCommandPool p_vkDestroyCommandPool;
static PFN_vkAllocateCommandBuffers p_vkAllocateCommandBuffers;
static PFN_vkBeginCommandBuffer p_vkBeginCommandBuffer;
static PFN_vkEndCommandBuffer p_vkEndCommandBuffer;
static PFN_vkCmdBindPipeline p_vkCmdBindPipeline;
static PFN_vkCmdBindDescriptorSets p_vkCmdBindDescriptorSets;
static PFN_vkCmdDispatch p_vkCmdDispatch;
static PFN_vkCreateFence p_vkCreateFence;
static PFN_vkDestroyFence p_vkDestroyFence;
static PFN_vkWaitForFences p_vkWaitForFences;
static PFN_vkQueueSubmit p_vkQueueSubmit;

static int load_instance_procs(VkInstance instance)
{
#define LI(sym)                                                             \
    do {                                                                    \
        p_##sym = (PFN_##sym)g_gipa(instance, #sym);                        \
        if (!p_##sym) {                                                     \
            fprintf(stderr, "ERR: missing instance proc " #sym "\n");      \
            return -1;                                                      \
        }                                                                   \
    } while (0)
    LI(vkDestroyInstance);
    LI(vkEnumeratePhysicalDevices);
    LI(vkGetPhysicalDeviceProperties);
    LI(vkGetPhysicalDeviceQueueFamilyProperties);
    LI(vkGetPhysicalDeviceMemoryProperties);
    LI(vkCreateDevice);
    LI(vkDestroyDevice);
    LI(vkGetDeviceProcAddr);
#undef LI
    return 0;
}

static int load_device_procs(VkDevice device)
{
#define LD(sym)                                                             \
    do {                                                                    \
        p_##sym = (PFN_##sym)p_vkGetDeviceProcAddr(device, #sym);           \
        if (!p_##sym) {                                                     \
            fprintf(stderr, "ERR: missing device proc " #sym "\n");        \
            return -1;                                                      \
        }                                                                   \
    } while (0)
    LD(vkGetDeviceQueue);
    LD(vkCreateBuffer);
    LD(vkDestroyBuffer);
    LD(vkGetBufferMemoryRequirements);
    LD(vkAllocateMemory);
    LD(vkFreeMemory);
    LD(vkBindBufferMemory);
    LD(vkMapMemory);
    LD(vkUnmapMemory);
    LD(vkCreateShaderModule);
    LD(vkDestroyShaderModule);
    LD(vkCreateDescriptorSetLayout);
    LD(vkDestroyDescriptorSetLayout);
    LD(vkCreatePipelineLayout);
    LD(vkDestroyPipelineLayout);
    LD(vkCreateComputePipelines);
    LD(vkDestroyPipeline);
    LD(vkCreateDescriptorPool);
    LD(vkDestroyDescriptorPool);
    LD(vkAllocateDescriptorSets);
    LD(vkUpdateDescriptorSets);
    LD(vkCreateCommandPool);
    LD(vkDestroyCommandPool);
    LD(vkAllocateCommandBuffers);
    LD(vkBeginCommandBuffer);
    LD(vkEndCommandBuffer);
    LD(vkCmdBindPipeline);
    LD(vkCmdBindDescriptorSets);
    LD(vkCmdDispatch);
    LD(vkCreateFence);
    LD(vkDestroyFence);
    LD(vkWaitForFences);
    LD(vkQueueSubmit);
#undef LD
    return 0;
}

/* Runs the vector-add compute on (phys, device, qfam). Returns 0 on pass, 13 on any failure. */
static int run_compute(VkPhysicalDevice phys, VkDevice device, uint32_t qfam)
{
    VkResult r;
    VkQueue queue;
    p_vkGetDeviceQueue(device, qfam, 0, &queue);

    const VkDeviceSize bufSize = (VkDeviceSize)ELEM_COUNT * sizeof(float);
    VkBuffer bufs[3];
    VkDeviceMemory mems[3];
    memset(bufs, 0, sizeof(bufs));
    memset(mems, 0, sizeof(mems));

    for (int i = 0; i < 3; i++) {
        VkBufferCreateInfo bci;
        memset(&bci, 0, sizeof(bci));
        bci.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
        bci.size = bufSize;
        bci.usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        r = p_vkCreateBuffer(device, &bci, NULL, &bufs[i]);
        if (r != VK_SUCCESS) {
            fprintf(stderr, "ERR: vkCreateBuffer %d failed: %d\n", i, (int)r);
            return 13;
        }
        VkMemoryRequirements mr;
        p_vkGetBufferMemoryRequirements(device, bufs[i], &mr);
        VkPhysicalDeviceMemoryProperties mp;
        p_vkGetPhysicalDeviceMemoryProperties(phys, &mp);
        uint32_t memType = UINT32_MAX;
        const VkMemoryPropertyFlags want =
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        for (uint32_t m = 0; m < mp.memoryTypeCount; m++) {
            if ((mr.memoryTypeBits & (1u << m)) &&
                (mp.memoryTypes[m].propertyFlags & want) == want) {
                memType = m;
                break;
            }
        }
        if (memType == UINT32_MAX) {
            fprintf(stderr, "ERR: no host-visible+coherent memory type for buffer %d\n", i);
            return 13;
        }
        VkMemoryAllocateInfo mai;
        memset(&mai, 0, sizeof(mai));
        mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        mai.allocationSize = mr.size;
        mai.memoryTypeIndex = memType;
        r = p_vkAllocateMemory(device, &mai, NULL, &mems[i]);
        if (r != VK_SUCCESS) {
            fprintf(stderr, "ERR: vkAllocateMemory %d failed: %d\n", i, (int)r);
            return 13;
        }
        r = p_vkBindBufferMemory(device, bufs[i], mems[i], 0);
        if (r != VK_SUCCESS) {
            fprintf(stderr, "ERR: vkBindBufferMemory %d failed: %d\n", i, (int)r);
            return 13;
        }
    }

    /* Fill inputs: A[i]=i, B[i]=2*i. */
    float *pa = NULL, *pb = NULL;
    p_vkMapMemory(device, mems[0], 0, bufSize, 0, (void **)&pa);
    p_vkMapMemory(device, mems[1], 0, bufSize, 0, (void **)&pb);
    if (!pa || !pb) {
        fprintf(stderr, "ERR: vkMapMemory failed for inputs\n");
        return 13;
    }
    for (uint32_t i = 0; i < ELEM_COUNT; i++) {
        pa[i] = (float)i;
        pb[i] = (float)(2u * i);
    }
    p_vkUnmapMemory(device, mems[0]);
    p_vkUnmapMemory(device, mems[1]);

    /* Shader module from the embedded SPIR-V. */
    VkShaderModuleCreateInfo sci;
    memset(&sci, 0, sizeof(sci));
    sci.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    sci.codeSize = sizeof(kShaderSpv);
    sci.pCode = kShaderSpv;
    VkShaderModule shader = VK_NULL_HANDLE;
    r = p_vkCreateShaderModule(device, &sci, NULL, &shader);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateShaderModule failed: %d\n", (int)r);
        return 13;
    }

    /* Descriptor set layout: 3 storage buffers, compute stage. */
    VkDescriptorSetLayoutBinding bindings[3];
    for (int i = 0; i < 3; i++) {
        memset(&bindings[i], 0, sizeof(bindings[i]));
        bindings[i].binding = (uint32_t)i;
        bindings[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        bindings[i].descriptorCount = 1;
        bindings[i].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    }
    VkDescriptorSetLayoutCreateInfo dsli;
    memset(&dsli, 0, sizeof(dsli));
    dsli.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    dsli.bindingCount = 3;
    dsli.pBindings = bindings;
    VkDescriptorSetLayout dsl = VK_NULL_HANDLE;
    r = p_vkCreateDescriptorSetLayout(device, &dsli, NULL, &dsl);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateDescriptorSetLayout failed: %d\n", (int)r);
        return 13;
    }

    VkPipelineLayoutCreateInfo pli;
    memset(&pli, 0, sizeof(pli));
    pli.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    pli.setLayoutCount = 1;
    pli.pSetLayouts = &dsl;
    VkPipelineLayout pl = VK_NULL_HANDLE;
    r = p_vkCreatePipelineLayout(device, &pli, NULL, &pl);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreatePipelineLayout failed: %d\n", (int)r);
        return 13;
    }

    VkPipelineShaderStageCreateInfo stage;
    memset(&stage, 0, sizeof(stage));
    stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    stage.module = shader;
    stage.pName = "main";
    VkComputePipelineCreateInfo pci;
    memset(&pci, 0, sizeof(pci));
    pci.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
    pci.stage = stage;
    pci.layout = pl;
    VkPipeline pipe = VK_NULL_HANDLE;
    r = p_vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &pci, NULL, &pipe);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateComputePipelines failed: %d\n", (int)r);
        return 13;
    }

    /* Descriptor pool + set. */
    VkDescriptorPoolSize poolSize;
    memset(&poolSize, 0, sizeof(poolSize));
    poolSize.type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    poolSize.descriptorCount = 3;
    VkDescriptorPoolCreateInfo dpi;
    memset(&dpi, 0, sizeof(dpi));
    dpi.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    dpi.maxSets = 1;
    dpi.poolSizeCount = 1;
    dpi.pPoolSizes = &poolSize;
    VkDescriptorPool pool = VK_NULL_HANDLE;
    r = p_vkCreateDescriptorPool(device, &dpi, NULL, &pool);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateDescriptorPool failed: %d\n", (int)r);
        return 13;
    }
    VkDescriptorSetAllocateInfo dai;
    memset(&dai, 0, sizeof(dai));
    dai.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    dai.descriptorPool = pool;
    dai.descriptorSetCount = 1;
    dai.pSetLayouts = &dsl;
    VkDescriptorSet dset = VK_NULL_HANDLE;
    r = p_vkAllocateDescriptorSets(device, &dai, &dset);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkAllocateDescriptorSets failed: %d\n", (int)r);
        return 13;
    }
    VkDescriptorBufferInfo binfo[3];
    VkWriteDescriptorSet writes[3];
    for (int i = 0; i < 3; i++) {
        memset(&binfo[i], 0, sizeof(binfo[i]));
        binfo[i].buffer = bufs[i];
        binfo[i].offset = 0;
        binfo[i].range = bufSize;
        memset(&writes[i], 0, sizeof(writes[i]));
        writes[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        writes[i].dstSet = dset;
        writes[i].dstBinding = (uint32_t)i;
        writes[i].descriptorCount = 1;
        writes[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writes[i].pBufferInfo = &binfo[i];
    }
    p_vkUpdateDescriptorSets(device, 3, writes, 0, NULL);

    /* Command buffer: bind + dispatch. */
    VkCommandPoolCreateInfo cpi;
    memset(&cpi, 0, sizeof(cpi));
    cpi.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    cpi.queueFamilyIndex = qfam;
    VkCommandPool cpool = VK_NULL_HANDLE;
    r = p_vkCreateCommandPool(device, &cpi, NULL, &cpool);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateCommandPool failed: %d\n", (int)r);
        return 13;
    }
    VkCommandBufferAllocateInfo cai;
    memset(&cai, 0, sizeof(cai));
    cai.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cai.commandPool = cpool;
    cai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cai.commandBufferCount = 1;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    r = p_vkAllocateCommandBuffers(device, &cai, &cmd);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkAllocateCommandBuffers failed: %d\n", (int)r);
        return 13;
    }
    VkCommandBufferBeginInfo bbi;
    memset(&bbi, 0, sizeof(bbi));
    bbi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    p_vkBeginCommandBuffer(cmd, &bbi);
    p_vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipe);
    p_vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pl,
                             0, 1, &dset, 0, NULL);
    p_vkCmdDispatch(cmd, (ELEM_COUNT + LOCAL_SIZE - 1u) / LOCAL_SIZE, 1, 1);
    r = p_vkEndCommandBuffer(cmd);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkEndCommandBuffer failed: %d\n", (int)r);
        return 13;
    }

    VkFenceCreateInfo fci;
    memset(&fci, 0, sizeof(fci));
    fci.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    VkFence fence = VK_NULL_HANDLE;
    r = p_vkCreateFence(device, &fci, NULL, &fence);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateFence failed: %d\n", (int)r);
        return 13;
    }
    VkSubmitInfo si;
    memset(&si, 0, sizeof(si));
    si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    si.commandBufferCount = 1;
    si.pCommandBuffers = &cmd;
    r = p_vkQueueSubmit(queue, 1, &si, fence);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkQueueSubmit failed: %d\n", (int)r);
        return 13;
    }
    r = p_vkWaitForFences(device, 1, &fence, VK_TRUE, 10000000000ull); /* 10 s */
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkWaitForFences failed/timed out: %d\n", (int)r);
        return 13;
    }

    /* Read back and verify: C[i] must equal 3*i exactly. */
    float *pc = NULL;
    p_vkMapMemory(device, mems[2], 0, bufSize, 0, (void **)&pc);
    if (!pc) {
        fprintf(stderr, "ERR: vkMapMemory failed for output\n");
        return 13;
    }
    int bad = 0;
    for (uint32_t i = 0; i < ELEM_COUNT; i++) {
        float expect = (float)(3u * i);
        if (pc[i] != expect) {
            if (bad < 5)
                fprintf(stderr, "ERR: C[%u]=%f, expected %f\n", i, pc[i], expect);
            bad++;
        }
    }
    p_vkUnmapMemory(device, mems[2]);
    if (bad > 0) {
        fprintf(stderr, "ERR: %d/%u output elements wrong\n", bad, ELEM_COUNT);
        return 13;
    }

    printf("COMPUTE PASS: %u-element vector add verified (c[i] = a[i] + b[i])\n",
           ELEM_COUNT);
    fflush(stdout);
    return 0;
}

int main(void)
{
    /* 1. Load the Vulkan loader at runtime (no link-time dependency). */
    void *lib = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!lib) {
        fprintf(stderr, "ERR: dlopen(\"libvulkan.so.1\") failed: %s\n",
                dlerror() ? dlerror() : "unknown");
        return 9;
    }
    g_gipa = (PFN_vkGetInstanceProcAddr)dlsym(lib, "vkGetInstanceProcAddr");
    if (!g_gipa) {
        fprintf(stderr, "ERR: dlsym(vkGetInstanceProcAddr) failed\n");
        return 9;
    }
    p_vkCreateInstance =
        (PFN_vkCreateInstance)g_gipa(NULL, "vkCreateInstance");
    if (!p_vkCreateInstance) {
        fprintf(stderr, "ERR: vkCreateInstance entry point not found\n");
        return 10;
    }

    /* 2. Create instance. No extensions: compute-only, no surface, no display. */
    VkApplicationInfo app;
    memset(&app, 0, sizeof(app));
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "vk-compute-test";
    app.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    app.pEngineName = "droiddeck-comptest";
    app.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo ici;
    memset(&ici, 0, sizeof(ici));
    ici.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ici.pApplicationInfo = &app;
    VkInstance instance = VK_NULL_HANDLE;
    VkResult r = p_vkCreateInstance(&ici, NULL, &instance);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateInstance failed: %d\n", (int)r);
        return 10;
    }
    if (load_instance_procs(instance) != 0)
        return 10;

    /* 3. Enumerate physical devices. */
    uint32_t devCount = 0;
    r = p_vkEnumeratePhysicalDevices(instance, &devCount, NULL);
    if (r != VK_SUCCESS || devCount == 0) {
        fprintf(stderr, "ERR: no physical devices enumerated (count=%u, res=%d)\n",
                devCount, (int)r);
        p_vkDestroyInstance(instance, NULL);
        return 11;
    }
    VkPhysicalDevice *devs =
        (VkPhysicalDevice *)malloc(devCount * sizeof(*devs));
    if (!devs) {
        fprintf(stderr, "ERR: out of memory\n");
        p_vkDestroyInstance(instance, NULL);
        return 11;
    }
    p_vkEnumeratePhysicalDevices(instance, &devCount, devs);
    for (uint32_t i = 0; i < devCount; i++) {
        VkPhysicalDeviceProperties props;
        p_vkGetPhysicalDeviceProperties(devs[i], &props);
        printf("device %u: name=\"%s\" driverVersion=0x%08x apiVersion=%u.%u.%u\n",
               i, props.deviceName, props.driverVersion,
               VK_VERSION_MAJOR(props.apiVersion),
               VK_VERSION_MINOR(props.apiVersion),
               VK_VERSION_PATCH(props.apiVersion));
    }
    fflush(stdout);
    VkPhysicalDevice phys = devs[0];
    free(devs);

    /* 4. Find a compute-capable queue family. */
    uint32_t qCount = 0;
    p_vkGetPhysicalDeviceQueueFamilyProperties(phys, &qCount, NULL);
    VkQueueFamilyProperties *qfams =
        (VkQueueFamilyProperties *)malloc(
            (qCount ? qCount : 1) * sizeof(*qfams));
    if (!qfams) {
        fprintf(stderr, "ERR: out of memory\n");
        p_vkDestroyInstance(instance, NULL);
        return 12;
    }
    p_vkGetPhysicalDeviceQueueFamilyProperties(phys, &qCount, qfams);
    uint32_t qfam = UINT32_MAX;
    for (uint32_t i = 0; i < qCount; i++) {
        if (qfams[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
            qfam = i;
            break;
        }
    }
    free(qfams);
    if (qfam == UINT32_MAX) {
        fprintf(stderr, "ERR: no compute-capable queue family found\n");
        p_vkDestroyInstance(instance, NULL);
        return 12;
    }
    printf("using device 0, compute queue family %u\n", qfam);
    fflush(stdout);

    /* 5. Create the logical device. */
    float prio = 1.0f;
    VkDeviceQueueCreateInfo qci;
    memset(&qci, 0, sizeof(qci));
    qci.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    qci.queueFamilyIndex = qfam;
    qci.queueCount = 1;
    qci.pQueuePriorities = &prio;
    VkDeviceCreateInfo dci;
    memset(&dci, 0, sizeof(dci));
    dci.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos = &qci;
    VkDevice device = VK_NULL_HANDLE;
    r = p_vkCreateDevice(phys, &dci, NULL, &device);
    if (r != VK_SUCCESS) {
        fprintf(stderr, "ERR: vkCreateDevice failed: %d\n", (int)r);
        p_vkDestroyInstance(instance, NULL);
        return 12;
    }
    if (load_device_procs(device) != 0) {
        p_vkDestroyDevice(device, NULL);
        p_vkDestroyInstance(instance, NULL);
        return 13;
    }

    /* 6. Run the compute test. */
    int rc = run_compute(phys, device, qfam);

    p_vkDestroyDevice(device, NULL);
    p_vkDestroyInstance(instance, NULL);
    dlclose(lib);
    return rc;
}

//
// Created by tainzhi on 2025/4/23.
// Email: qfq61@qqc.com
//

#ifndef TCAMERA_VULKAN_PROCESSOR_H
#define TCAMERA_VULKAN_PROCESSOR_H

#include <vulkan/vulkan.h>


VkInstance instances;
VkDebugUtilsMessengerEXT debugMessenger;
VkPhysicalDevice physicalDevice;
uint32_t queueFamilyIndex = 0;
VkDevice device;
VkQueue queue;
VkDescriptorSetLayout setLayout;
VkPipelineLayout pipelineLayout;


#endif //TCAMERA_VULKAN_PROCESSOR_H

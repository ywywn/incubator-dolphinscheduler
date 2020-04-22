package org.apache.dolphinscheduler.common.task.kafka;

import org.apache.dolphinscheduler.common.process.ResourceInfo;
import org.apache.dolphinscheduler.common.task.AbstractParameters;

import java.util.List;
import java.util.stream.Collectors;

public class KafkaParameters extends AbstractParameters {

    /**
     * kafka from shell script
     */
    private String rawScript;

    /**
     * kafka resource list from shell
     */
    private List<ResourceInfo> resourceList;

    public String getRawScript() {
        return rawScript;
    }

    public void setRawScript(String rawScript) {
        this.rawScript = rawScript;
    }

    public List<ResourceInfo> getResourceList() {
        return resourceList;
    }

    public void setResourceList(List<ResourceInfo> resourceList) {
        this.resourceList = resourceList;
    }

    @Override
    public boolean checkParameters() {
        return rawScript != null && !rawScript.isEmpty();
    }

    @Override
    public List<String> getResourceFilesList() {
        if (resourceList != null) {
            return resourceList.stream()
                    .map(p -> p.getRes()).collect(Collectors.toList());
        }

        return null;
    }

}

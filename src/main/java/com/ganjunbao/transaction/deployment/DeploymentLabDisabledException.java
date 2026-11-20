package com.ganjunbao.transaction.deployment;

public class DeploymentLabDisabledException extends RuntimeException {

    public DeploymentLabDisabledException() {
        super("部署实验功能未开启");
    }
}

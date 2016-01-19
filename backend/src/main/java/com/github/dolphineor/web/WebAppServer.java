package com.github.dolphineor.web;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.handlers.DefaultServlet;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.Resource;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/**
 * Created on 2016-01-16.
 *
 * @author dolphineor
 */
public class WebAppServer implements DisposableBean {
    private final Logger logger = LoggerFactory.getLogger(WebAppServer.class);

    private String webAppName;
    private Resource webAppRoot;
    private int port;

    private Undertow undertowServer;
    private DeploymentManager deploymentManager;

    public WebAppServer(String webAppName, Resource webAppRoot, int port) {
        this.webAppName = webAppName;
        this.webAppRoot = webAppRoot;
        this.port = port;
    }

    public WebAppServer start() throws IOException, ServletException {
        InstanceFactory<WebAppServletContainerInitializer> instanceFactory =
                new ImmediateInstanceFactory<>(new WebAppServletContainerInitializer());
        ServletContainerInitializerInfo sciInfo = new ServletContainerInitializerInfo(
                WebAppServletContainerInitializer.class, instanceFactory, new HashSet<>()
        );

        File webAppRootFile = webAppRoot.getFile();
        DeploymentInfo deploymentInfo = Servlets.deployment()
                .addServletContainerInitalizer(sciInfo)
                .setClassLoader(WebAppServer.class.getClassLoader())
                .setContextPath(webAppName)
                .setDeploymentName(webAppName + "-war")
                .setResourceManager(new FileResourceManager(webAppRootFile, 0))
                .addServlet(Servlets.servlet("default", DefaultServlet.class));
        deploymentManager = Servlets.defaultContainer().addDeployment(deploymentInfo);
        deploymentManager.deploy();

        HttpHandler httpHandler = deploymentManager.start();
        PathHandler pathHandler = Handlers.path(Handlers.redirect("/" + webAppName));
        pathHandler.addPrefixPath("/" + webAppName, httpHandler);

        undertowServer = Undertow.builder()
                .addHttpListener(port, "localhost")
                .setHandler(httpHandler)
                .build();

        undertowServer.start();

        return this;
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Stopping Undertow web server on port " + port);
        undertowServer.stop();
        deploymentManager.stop();
        deploymentManager.undeploy();
        logger.info("Undertow web server on port " + port + " stopped");
    }
}
/*
 *  TeleStax, Open Source Cloud Communications
 *  Copyright 2011-2018, Telestax Inc and individual contributors
 *  by the @authors tag.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation; either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.restcomm.connect.extension.controller;

import java.util.List;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.extension.api.ApiRequest;
import org.restcomm.connect.extension.api.ExtensionRequest;
import org.restcomm.connect.extension.api.ExtensionResponse;
import org.restcomm.connect.extension.api.ExtensionType;
import org.restcomm.connect.extension.api.IExtensionRequest;
import org.restcomm.connect.extension.api.RestcommExtension;
import org.restcomm.connect.extension.api.RestcommExtensionGeneric;

public class ExtensionsControllerTest {

    private static Logger logger = Logger.getLogger(ExtensionsControllerTest.class);

    private RestcommExtensionGeneric testExtension = new TestExtension();

    @After
    public void resteController() {
        //reset the singleton after each test to provide isolation and
        //predictibiltiy when full test class is executed
        ExtensionController.getInstance().reset();
    }

    @Test
    public void extensionRegistry() {
        ExtensionController extensionController = ExtensionController.getInstance();
        extensionController.registerExtension(testExtension);

        List<RestcommExtensionGeneric> callManagerExts = extensionController.getExtensions(ExtensionType.CallManager);
        logger.info("CallManagerExtensions list size: "+callManagerExts.size());
        for (RestcommExtensionGeneric ext: callManagerExts) {
            logger.info("ExtensionName: "+ext.getName());
        }

        Assert.assertEquals(1, callManagerExts.size());
        Assert.assertEquals(1, extensionController.getExtensions(ExtensionType.FeatureAccessControl).size());
        Assert.assertEquals(1, extensionController.getExtensions(ExtensionType.RestApi).size());
        Assert.assertEquals(1, extensionController.getExtensions(ExtensionType.SmsService).size());
        Assert.assertEquals(1, extensionController.getExtensions(ExtensionType.UssdCallManager).size());
    }

    @Test
    public void preInboundActionTest() {
        ExtensionController extensionController = ExtensionController.getInstance();
        extensionController.registerExtension(testExtension);

        List<RestcommExtensionGeneric> extensions = extensionController.getExtensions(ExtensionType.FeatureAccessControl);

        IExtensionRequest request = new ExtensionRequest();

        ExtensionResponse er = extensionController.executePreInboundAction(request, extensions);
        Assert.assertNotNull(er);
        Assert.assertTrue(er.isAllowed());
    }

    @Test
    public void postInboundActionTest() {
        ExtensionController extensionController = ExtensionController.getInstance();
        extensionController.registerExtension(testExtension);

        List<RestcommExtensionGeneric> extensions = extensionController.getExtensions(ExtensionType.FeatureAccessControl);

        IExtensionRequest request = new ExtensionRequest();

        ExtensionResponse er = extensionController.executePostInboundAction(request, extensions);
        Assert.assertNotNull(er);
        Assert.assertTrue(er.isAllowed());
    }


    @Test
    public void preOutboundActionTest() {
        ExtensionController extensionController = ExtensionController.getInstance();
        extensionController.registerExtension(testExtension);

        List<RestcommExtensionGeneric> extensions = extensionController.getExtensions(ExtensionType.FeatureAccessControl);

        IExtensionRequest request = new ExtensionRequest();

        ExtensionResponse er = extensionController.executePreOutboundAction(request, extensions);
        Assert.assertNotNull(er);
        Assert.assertTrue(er.isAllowed());
    }

    @Test
    public void postOutboundActionTest() {
        ExtensionController extensionController = ExtensionController.getInstance();
        extensionController.registerExtension(testExtension);

        List<RestcommExtensionGeneric> extensions = extensionController.getExtensions(ExtensionType.FeatureAccessControl);

        IExtensionRequest request = new ExtensionRequest();

        ExtensionResponse er = extensionController.executePostOutboundAction(request, extensions);
        Assert.assertNotNull(er);
        Assert.assertTrue(er.isAllowed());
    }

    @Test
    public void preApiActionTest() {
        ExtensionController extensionController = ExtensionController.getInstance();
        extensionController.registerExtension(testExtension);

        List<RestcommExtensionGeneric> extensions = extensionController.getExtensions(ExtensionType.FeatureAccessControl);

        Sid accSid = Sid.generate(Sid.Type.ACCOUNT);
        ApiRequest apiRequest = new ApiRequest(accSid.toString(), null, ApiRequest.Type.CREATE_SUBACCOUNT);

        ExtensionResponse er = extensionController.executePreApiAction(apiRequest, extensions);
        Assert.assertNotNull(er);
        Assert.assertTrue(er.isAllowed());
    }

    @Test
    public void postApiActionTest() {
        ExtensionController extensionController = ExtensionController.getInstance();
        extensionController.registerExtension(testExtension);

        List<RestcommExtensionGeneric> extensions = extensionController.getExtensions(ExtensionType.FeatureAccessControl);

        Sid accSid = Sid.generate(Sid.Type.ACCOUNT);
        ApiRequest apiRequest = new ApiRequest(accSid.toString(), null, ApiRequest.Type.CREATE_SUBACCOUNT);

        ExtensionResponse er = extensionController.executePostApiAction(apiRequest, extensions);
        Assert.assertNotNull(er);
        Assert.assertTrue(er.isAllowed());
    }

    @RestcommExtension(author = "TestExtension", version = "1.0.0.Alpha", type = {ExtensionType.CallManager, ExtensionType.SmsService, ExtensionType.UssdCallManager, ExtensionType.FeatureAccessControl, ExtensionType.RestApi})
    private class TestExtension implements RestcommExtensionGeneric {

        @Override
        public void init (ServletContext context) {

        }

        @Override
        public boolean isEnabled () {
            return true;
        }

        @Override
        public ExtensionResponse preInboundAction (IExtensionRequest extensionRequest) {
            return null;
        }

        @Override
        public ExtensionResponse postInboundAction (IExtensionRequest extensionRequest) {
            return null;
        }

        @Override
        public ExtensionResponse preOutboundAction (IExtensionRequest extensionRequest) {
            return null;
        }

        @Override
        public ExtensionResponse postOutboundAction (IExtensionRequest extensionRequest) {
            return null;
        }

        @Override
        public ExtensionResponse preApiAction (ApiRequest apiRequest) {
            return null;
        }

        @Override
        public ExtensionResponse postApiAction (ApiRequest apiRequest) {
            return null;
        }

        @Override
        public String getName () {
            return "TestExtension";
        }

        @Override
        public String getVersion () {
            return null;
        }
    }

}

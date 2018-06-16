/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */

package org.restcomm.connect.dao.mybatis;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * @author orestis.tsakiridis@telestax.com - Orestis Tsakiridis
 */
public class DaoTest {

    File sandboxRoot; // rot directory where mybatis files will be created in

    protected static Sid instanceId = Sid.generate(Sid.Type.INSTANCE);
    /**
     * Create a temporary directory with random name inside the system temporary directory.
     * Provide a prefix that will be used when creating the name too.
     *
     * @param prefix
     * @return
     */
    public File createTempDir(String prefix) {
        String tempDirLocation = generateTempDirName(prefix);
        File tempDir = new File(tempDirLocation);
        tempDir.mkdir();

        return tempDir;
    }

    private String generateTempDirName(String prefix) {
        String tempDirLocationRoot = System.getProperty("java.io.tmpdir");
        Random ran = new Random();
        return tempDirLocationRoot + "/" + prefix + ran.nextInt(10000);
    }

    /**
     * Remove a temporary directory. Use this to couple createTempDir().
     *
     * @param tempDirLocation
     */
    public void removeTempDir(String tempDirLocation) {
        File tempDir = new File(tempDirLocation);
        FileUtils.deleteQuietly(tempDir);
    }

    public static void setupSandbox(String mybatisFilesSource, File sandboxDir) throws IOException {
        File mybatisDir = new File(mybatisFilesSource);
        FileUtils.copyDirectory(mybatisDir, sandboxDir);
        // replace mybatis descriptors path inside sandbox mybatis.xml
        String mybatisXmlPath = sandboxDir.getAbsolutePath() + "/mybatis.xml";
        String content = FileUtils.readFileToString(new File(mybatisXmlPath));
        content = content.replaceAll("MYBATIS_SANDBOX_PATH",sandboxDir.getAbsolutePath());
        FileUtils.writeStringToFile(new File(sandboxDir.getAbsolutePath() + "/mybatis_updated.xml"),content);

        XMLConfiguration xmlConfiguration = new XMLConfiguration();
        xmlConfiguration.setDelimiterParsingDisabled(true);
        xmlConfiguration.setAttributeSplittingDisabled(true);
        try {
			xmlConfiguration.load("restcomm.xml");
	        RestcommConfiguration.createOnce(xmlConfiguration);
	        RestcommConfiguration.getInstance().getMain().setInstanceId(instanceId.toString());
		} catch (ConfigurationException e) {
			e.printStackTrace();
		}
    }
}

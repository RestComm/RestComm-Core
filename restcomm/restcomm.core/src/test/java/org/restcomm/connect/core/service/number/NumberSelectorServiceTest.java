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
package org.restcomm.connect.core.service.number;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;

import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.number.NumberSelectorServiceImpl;
import org.restcomm.connect.dao.IncomingPhoneNumbersDao;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.IncomingPhoneNumberFilter;

public class NumberSelectorServiceTest {

    public NumberSelectorServiceTest() {
    }

    @Test
    public void testNoMatch() {
        Sid srcSid = Sid.generate(Sid.Type.ORGANIZATION);
        Sid destSid = srcSid;
        String number = "1234";
        List<IncomingPhoneNumber> emptyNumbers = new ArrayList();

        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyNumbers, emptyNumbers, emptyNumbers);
        when(numDao.getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyNumbers);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(0,0,0);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber(number, srcSid, destSid);

        Assert.assertNull(found);
        inOrder.verify(numDao, times(3)).getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any());
    }

    @Test
    public void testPerfectMatch() {
        Sid srcSid = Sid.generate(Sid.Type.ORGANIZATION);
        Sid destSid = Sid.generate(Sid.Type.ORGANIZATION);
        String number = "1234";
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(number);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber(number, srcSid, destSid);

        Assert.assertNotNull(found);
        Assert.assertEquals(number, found.getPhoneNumber());
        inOrder.verify(numDao, times(1)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        verify(numDao, never()).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }

    @Test
    public void testUSMatch() {
        Sid srcSid = Sid.generate(Sid.Type.ORGANIZATION);
        Sid destSid = Sid.generate(Sid.Type.ORGANIZATION);
        String number = "+1234";
        List<IncomingPhoneNumber> emptyList = new ArrayList();
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(number);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1,1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyList, numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber("1234", srcSid, destSid);

        Assert.assertNotNull(found);
        Assert.assertEquals(number, found.getPhoneNumber());
        inOrder.verify(numDao, times(2)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        verify(numDao, never()).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }

    @Test
    public void testNoPlusMatch() {
        Sid srcSid = Sid.generate(Sid.Type.ORGANIZATION);
        Sid destSid = Sid.generate(Sid.Type.ORGANIZATION);
        String number = "1234";
        List<IncomingPhoneNumber> emptyList = new ArrayList();
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(number);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1,1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyList, numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber("+1234", srcSid, destSid);

        Assert.assertNotNull(found);
        Assert.assertEquals(number, found.getPhoneNumber());
        inOrder.verify(numDao, times(2)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        verify(numDao, never()).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }

    @Test
    public void testPerfectMatchNoOrg() {
        Sid srcSid = null;
        Sid destSid = null;
        String number = "1234";
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(number);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber(number, srcSid, destSid);

        Assert.assertNotNull(found);
        Assert.assertEquals(number, found.getPhoneNumber());
        inOrder.verify(numDao, times(1)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        verify(numDao, never()).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }

    @Test
    public void testStarMatch() {
        Sid srcSid = Sid.generate(Sid.Type.ORGANIZATION);
        Sid destSid = srcSid;
        String number = "1234";
        String star = "*";
        List<IncomingPhoneNumber> emptyList = new ArrayList();
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(star);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1,1,1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyList,emptyList, numbers);
        when(numDao.getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyList);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber(number, srcSid, destSid);

        Assert.assertNotNull(found);
        Assert.assertEquals(star, found.getPhoneNumber());
        inOrder.verify(numDao, times(3)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
    }

    @Test
    public void testRegexNoOrg() {
        Sid srcSid = null;
        Sid destSid = null;
        String regex = "12        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).\n" +
"                thenReturn(1,1,1);.*";
        List<IncomingPhoneNumber> emptyNumbers = new ArrayList();
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(regex);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1,1,1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyNumbers, emptyNumbers);
        when(numDao.getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any())).
                thenReturn(numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber("1234", srcSid, destSid);

        Assert.assertNull(found);
        inOrder.verify(numDao, times(2)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        inOrder.verify(numDao, never()).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());

    }

    @Test
    public void testRegexMatch() {
        Sid srcSid = Sid.generate(Sid.Type.ORGANIZATION);
        Sid destSid = srcSid;
        String regex = "12.*";
        String longestRegex = "123.*";
        List<IncomingPhoneNumber> emptyNumbers = new ArrayList();
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(regex);
        numbers.add(builder.build());
        builder.setPhoneNumber(longestRegex);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1,1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyNumbers, emptyNumbers);
        when(numDao.getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any())).
                thenReturn(numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber("1234", srcSid, destSid);

        Assert.assertNotNull(found);
        Assert.assertEquals(longestRegex, found.getPhoneNumber());
        inOrder.verify(numDao, times(2)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        inOrder.verify(numDao, times(1)).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }

    @Test
    public void testRegexMatch2() {
        Sid srcSid = Sid.generate(Sid.Type.ORGANIZATION);
        Sid destSid = srcSid;
        String regex1 = "123456*";
        String regex2 = "554433*";
        String regex3 = "778899*";
        String regex4 = "987456*";
        String regex5 = "987456789*";
        String regex6 = "12*";
        String regex7 = "*";
        List<IncomingPhoneNumber> emptyNumbers = new ArrayList();
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(regex1);
        numbers.add(builder.build());
        builder.setPhoneNumber(regex2);
        numbers.add(builder.build());
        builder.setPhoneNumber(regex3);
        numbers.add(builder.build());
        builder.setPhoneNumber(regex4);
        numbers.add(builder.build());
        builder.setPhoneNumber(regex5);
        numbers.add(builder.build());
        builder.setPhoneNumber(regex6);
        numbers.add(builder.build());
        builder.setPhoneNumber(regex7);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1,1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyNumbers, emptyNumbers);
        when(numDao.getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any())).
                thenReturn(numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber("987456", srcSid, destSid);

        Assert.assertNotNull(found);
        Assert.assertEquals(regex4, found.getPhoneNumber());
        inOrder.verify(numDao, times(3)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        inOrder.verify(numDao, times(1)).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }


    @Test
    public void testRegexMatchFromNullSrcOrg() {
        Sid srcSid = null;
        Sid destSid = Sid.generate(Sid.Type.ORGANIZATION);
        String regex = "12.*";
        String longestRegex = "123.*";
        List<IncomingPhoneNumber> emptyNumbers = new ArrayList();
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(regex);
        numbers.add(builder.build());
        builder.setPhoneNumber(longestRegex);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1,1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyNumbers, emptyNumbers);
        when(numDao.getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any())).
                thenReturn(numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber("1234", srcSid, destSid);

        Assert.assertNotNull(found);
        Assert.assertEquals(longestRegex, found.getPhoneNumber());
        inOrder.verify(numDao, times(2)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        inOrder.verify(numDao, times(1)).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }

    @Test
    public void testRegexFailToMatchBecauseOfPhone() {
        Sid srcSid = null;
        Sid destSid = Sid.generate(Sid.Type.ORGANIZATION);
        String regex = "12.*";
        String longestRegex = "123.*";
        List<IncomingPhoneNumber> emptyNumbers = new ArrayList();
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(regex);
        numbers.add(builder.build());
        builder.setPhoneNumber(longestRegex);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1,1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyNumbers, emptyNumbers);
        when(numDao.getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any())).
                thenReturn(numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber("7788", srcSid, destSid);

        Assert.assertNull(found);
        inOrder.verify(numDao, times(4)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        inOrder.verify(numDao, never()).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }


    @Test
    public void testRegexFailToMatchBecauseDestOrgNull() {
        Sid srcSid = Sid.generate(Sid.Type.ORGANIZATION);
        Sid destSid = null;
        String regex = "12.*";
        String longestRegex = "123.*";
        List<IncomingPhoneNumber> emptyNumbers = new ArrayList();
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(regex);
        numbers.add(builder.build());
        builder.setPhoneNumber(longestRegex);
        numbers.add(builder.build());
        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1,1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(emptyNumbers, emptyNumbers);
        when(numDao.getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any())).
                thenReturn(numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber("1234", srcSid, destSid);

        Assert.assertNull(found);
        inOrder.verify(numDao, times(2)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        inOrder.verify(numDao, never()).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }

    @Test
    public void testSamePhoneDiffOrg() {
        Sid srcSid = Sid.generate(Sid.Type.ORGANIZATION);
        Sid srcSid2 = Sid.generate(Sid.Type.ORGANIZATION);
        Sid destSid = Sid.generate(Sid.Type.ORGANIZATION);
        String number = "1234";
        List<IncomingPhoneNumber> numbers = new ArrayList();
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setPhoneNumber(number);
        builder.setOrganizationSid(srcSid);
        numbers.add(builder.build());

        builder.setPhoneNumber(number);
        builder.setOrganizationSid(srcSid2);
        numbers.add(builder.build());

        IncomingPhoneNumbersDao numDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        when(numDao.getTotalIncomingPhoneNumbers((IncomingPhoneNumberFilter) any())).
                thenReturn(1);
        when(numDao.getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any())).
                thenReturn(numbers);
        InOrder inOrder = inOrder(numDao);
        NumberSelectorServiceImpl service = new NumberSelectorServiceImpl(numDao);

        IncomingPhoneNumber found = service.searchNumber(number, srcSid, destSid);

        Assert.assertNotNull(found);
        Assert.assertEquals(number, found.getPhoneNumber());
        inOrder.verify(numDao, times(1)).getIncomingPhoneNumbersByFilter((IncomingPhoneNumberFilter) any());
        verify(numDao, never()).getIncomingPhoneNumbersRegex((IncomingPhoneNumberFilter) any());
    }
}

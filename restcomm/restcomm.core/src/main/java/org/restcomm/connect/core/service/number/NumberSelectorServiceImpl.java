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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.api.NumberSelectorService;
import org.restcomm.connect.core.service.number.api.NumberSelectionResult;
import org.restcomm.connect.core.service.number.api.ResultType;
import org.restcomm.connect.core.service.number.api.SearchModifier;
import org.restcomm.connect.dao.IncomingPhoneNumbersDao;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.IncomingPhoneNumberFilter;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

/**
 * This Service will be used in different protocol scenarios to find if some
 * application is associated to the incoming session/number.
 *
 * Different queries to IncomingPhoneNumbersDao will be performed to try
 * locating the proper application.
 *
 *
 * Is expected that protocol scenario will provide meaningful Organization
 * details for source and destination. If protocol doesnt support Organizations
 * yet, then null values are allowed, but Regexes will not be evaluated in these
 * cases.
 */
public class NumberSelectorServiceImpl implements NumberSelectorService {

    private static Logger logger = Logger.getLogger(NumberSelectorServiceImpl.class);

    private IncomingPhoneNumbersDao numbersDao;

    public NumberSelectorServiceImpl(IncomingPhoneNumbersDao numbersDao) {
        this.numbersDao = numbersDao;
    }

    /**
     *
     * @param phone the original incoming phone number
     * @return a list of strings to match number based on different formats
     */
    private List<String> createPhoneQuery(String phone) {
        List<String> numberQueries = new ArrayList<String>(10);
        //add the phone itself like it is first
        numberQueries.add(phone);
        //try adding US format if number doesnt contain *#
        if (!(phone.contains("*") || phone.contains("#"))) {
            try {
                // Format the destination to an E.164 phone number.
                final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
                String usFormatNum = phoneNumberUtil.format(phoneNumberUtil.parse(phone, "US"), PhoneNumberUtil.PhoneNumberFormat.E164);
                if (!numberQueries.contains(usFormatNum)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Adding US Format num to queries:" + usFormatNum);
                    }
                    numberQueries.add(usFormatNum);
                }
            } catch (NumberParseException e) {
                //logger.error("Exception when try to format : " + e);
            }
        }
        //here we deal with different + prefix scnearios.
        //different providers will trigger calls with different formats
        //basiaclly we try with a leading + if original number doesnt have it,
        //and remove it if it has it.
        if (phone.startsWith("+")) {
            //remove the (+) and check if exists
            String noPlusNum = phone.replaceFirst("\\+", "");
            if (!numberQueries.contains(noPlusNum)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Adding No Plus Num:" + noPlusNum);
                }
                numberQueries.add(noPlusNum);
            }
        } else {
            String plusNum = "+".concat(phone);
            if (!numberQueries.contains(plusNum)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Adding Plus Num:" + plusNum);
                }
                numberQueries.add(plusNum);
            }
        }
        return numberQueries;
    }

    /**
     * Here we expect a perfect match in DB.
     *
     * Several rules regarding organization scoping will be applied in the DAO
     * filter to ensure only applicable numbers in DB are retrieved.
     *
     * @param number the number to match against IncomingPhoneNumbersDao
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @return the matched number, null if not matched.
     */
    private NumberSelectionResult findSingleNumber(String number,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid, Set<SearchModifier> modifiers) {
        NumberSelectionResult matchedNumber = new NumberSelectionResult(null, false, null);
        IncomingPhoneNumberFilter.Builder filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
        filterBuilder.byPhoneNumber(number);
        int unfilteredCount = numbersDao.getTotalIncomingPhoneNumbers(filterBuilder.build());
        if (unfilteredCount > 0) {
            if (destinationOrganizationSid != null) {
                filterBuilder.byOrgSid(destinationOrganizationSid.toString());
            } else if ((modifiers != null) && (modifiers.contains(SearchModifier.ORG_COMPLIANT))){
                //restrict search to non SIP numbers
                logger.debug("Organizations are null, restrict PureSIP numbers.");
                filterBuilder.byPureSIP(Boolean.FALSE);
            }

            //this rule forbids using PureSIP numbers if organizations doesnt match
            //this means only external provider numbers will be evaluated in DB
            if (sourceOrganizationSid != null
                    && !sourceOrganizationSid.equals(destinationOrganizationSid)) {
                filterBuilder.byPureSIP(Boolean.FALSE);
            }

            IncomingPhoneNumberFilter numFilter = filterBuilder.build();
            if (logger.isDebugEnabled()) {
                logger.debug("Searching with filter:" + numFilter);
            }
            List<IncomingPhoneNumber> matchedNumbers = numbersDao.getIncomingPhoneNumbersByFilter(numFilter);
            if (logger.isDebugEnabled()) {
                logger.debug("Num of results:" + matchedNumbers.size() + ".unfilteredCount:" + unfilteredCount);
            }
            //we expect a perfect match, so first result taken
            if (matchedNumbers != null && matchedNumbers.size() > 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Matched number with filter:" + matchedNumbers.get(0).toString());
                }

                matchedNumber = new NumberSelectionResult(matchedNumbers.get(0), Boolean.FALSE, ResultType.REGULAR);
            } else {
                //without organization fileter we had results,so this is
                //marked as filtered by organization
                matchedNumber.setOrganizationFiltered(Boolean.TRUE);
            }
        }
        return matchedNumber;
    }

    /**
     * Iterates over the list of given numbers, and returns the first matching.
     *
     * @param numberQueries the list of numbers to attempt
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @return the matched number, null if not matched.
     */
    private NumberSelectionResult findByNumber(List<String> numberQueries,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid, Set<SearchModifier> modifiers) {
        Boolean orgFiltered = false;
        NumberSelectionResult matchedNumber = new NumberSelectionResult(null, orgFiltered, null);
        int i = 0;
        while (matchedNumber.getNumber() == null && i < numberQueries.size()) {
            matchedNumber = findSingleNumber(numberQueries.get(i),
                    sourceOrganizationSid, destinationOrganizationSid, modifiers);
            //preserve the orgFiltered flag along the queries
            if (matchedNumber.getOrganizationFiltered()) {
                orgFiltered = true;
            }
            i = i + 1;
        }
        matchedNumber.setOrganizationFiltered(orgFiltered);
        return matchedNumber;
    }

    /**
     * @param phone
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @return
     */
    @Override
    public IncomingPhoneNumber searchNumber(String phone,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid) {
        return searchNumber(phone, sourceOrganizationSid, destinationOrganizationSid, new HashSet<>(Arrays.asList(SearchModifier.ORG_COMPLIANT)));
    }

    /**
     * @param phone
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @param modifiers
     * @return
     */
    @Override
    public IncomingPhoneNumber searchNumber(String phone,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid, Set<SearchModifier> modifiers) {
        NumberSelectionResult searchNumber = searchNumberWithResult(phone, sourceOrganizationSid, destinationOrganizationSid, modifiers);
        return searchNumber.getNumber();
    }

    /**
     *
     * @param result whether the call should be rejected depending on results
     * found
     * @param srcOrg
     * @param destOrg
     * @return
     */
    @Override
    public boolean isFailedCall(NumberSelectionResult result, Sid srcOrg, Sid destOrg) {
        boolean failCall = false;

        if (result.getNumber() == null) {
            if (!destOrg.equals(srcOrg)
                    && result.getOrganizationFiltered()) {
                failCall = true;
            }
        }

        return failCall;
    }

    /**
     * The main logic is: -Find a perfect match in DB using different formats.
     * -If not matched, use available Regexes in the organization. -If not
     * matched, try with the special * match.
     *
     * @param phone
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @return
     */
    @Override
    public NumberSelectionResult searchNumberWithResult(String phone,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid){
        return searchNumberWithResult(phone, sourceOrganizationSid, destinationOrganizationSid, new HashSet<>(Arrays.asList(SearchModifier.ORG_COMPLIANT)));
    }

    /**
     * The main logic is: -Find a perfect match in DB using different formats.
     * -If not matched, use available Regexes in the organization. -If not
     * matched, try with the special * match.
     *
     * @param phone
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @param modifiers
     * @return
     */
    @Override
    public NumberSelectionResult searchNumberWithResult(String phone,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid, Set<SearchModifier> modifiers) {
        if (logger.isDebugEnabled()) {
            logger.debug("getMostOptimalIncomingPhoneNumber: " + phone
                    + ",srcOrg:" + sourceOrganizationSid
                    + ",destOrg:" + destinationOrganizationSid);
        }
        List<String> numberQueries = createPhoneQuery(phone);

        NumberSelectionResult numberfound = findByNumber(numberQueries, sourceOrganizationSid, destinationOrganizationSid, modifiers);
        if (numberfound.getNumber() == null) {
            //only use regex if perfect match didnt worked
            if (destinationOrganizationSid != null
                    &&  (sourceOrganizationSid == null || destinationOrganizationSid.equals(sourceOrganizationSid))
                    && phone.matches("[\\d,*,#,+]+")) {
                //check regex if source and dest orgs are the same
                //only use regex if org available
                //check if there is a Regex match only if parameter is a String aka phone Number
                NumberSelectionResult regexFound = findByRegex(numberQueries, sourceOrganizationSid, destinationOrganizationSid);
                if (regexFound.getNumber() != null) {
                    numberfound = regexFound;
                }
                if (numberfound.getNumber() == null) {
                    //if no regex match found, try with special star number in the end
                    NumberSelectionResult starfound = findSingleNumber("*", sourceOrganizationSid, destinationOrganizationSid, modifiers);
                    if (starfound.getNumber() != null) {
                        numberfound = new NumberSelectionResult(starfound.getNumber(), false, ResultType.REGEX);
                    }
                }
            }
        }
        if (numberfound.getNumber() == null) {
            if (logger.isDebugEnabled()) {
                StringBuffer stringBuffer = new StringBuffer();

                stringBuffer.append("NumberSelectionService didn't match a number because: ");

                if (destinationOrganizationSid == null) {
                    stringBuffer.append(" - Destination Org is null - ");
                } else if (sourceOrganizationSid != null && !destinationOrganizationSid.equals(sourceOrganizationSid)) {
                    stringBuffer.append(" - Source Org is NOT null and DOESN'T match the Destination Org - ");
                } else if (!phone.matches("[\\d,*,#,+]+")) {
                    String msg = String.format(" - Phone %s doesn't match regex \"[\\\\d,*,#,+]+\" - ", phone);
                    stringBuffer.append(msg);
                } else {
                    String msg = String.format(" - Phone %s didn't match any of the Regex - ",phone);
                    stringBuffer.append(msg);
                }
                logger.debug(stringBuffer.toString());
            }
        }
        return numberfound;
    }

    /**
     * Used to order a collection by the size of PhoneNumber String
     */
    class NumberLengthComparator implements Comparator<IncomingPhoneNumber> {

        @Override
        public int compare(IncomingPhoneNumber o1, IncomingPhoneNumber o2) {
            //put o2 first to make longest first in coll
            int comparison = Integer.compare(o2.getPhoneNumber().length(), o1.getPhoneNumber().length());
            return comparison == 0 ? -1 : comparison;
        }

    }

    /**
     * This will take the regexes available in given organization, and evalute
     * them agsint the given list of numbers, returning the first match.
     *
     * The list of regexes will be ordered by length to ensure the longest
     * regexes matching any number in the list is returned first.
     *
     * In this case, organization details are required.
     *
     * @param numberQueries
     * @param sourceOrganizationSid
     * @param destOrg
     * @return the longest regex matching any number in the list, null if no
     * match
     */
    private NumberSelectionResult findByRegex(List<String> numberQueries,
            Sid sourceOrganizationSid, Sid destOrg) {
        NumberSelectionResult numberFound = new NumberSelectionResult(null, false, null);
        IncomingPhoneNumberFilter.Builder filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
        filterBuilder.byOrgSid(destOrg.toString());
        filterBuilder.byPureSIP(Boolean.TRUE);
        List<IncomingPhoneNumber> regexList = numbersDao.getIncomingPhoneNumbersRegex(filterBuilder.build());
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Found %d Regex IncomingPhone numbers.", regexList.size()));
        }
        //order by regex length

        Set<IncomingPhoneNumber> regexSet = new TreeSet<IncomingPhoneNumber>(new NumberLengthComparator());
        regexSet.addAll(regexList);
        if (regexList != null && regexList.size() > 0) {
            NumberSelectionResult matchingRegex = findFirstMatchingRegex(numberQueries, regexSet);
            if (matchingRegex.getNumber() != null) {
                numberFound = matchingRegex;
            }
        }

        return numberFound;
    }

    /**
     *
     * @param numberQueries the list of numbers to be matched
     * @param regexSet The set of regexes to evaluate against given numbers
     * @return the first regex matching any number in list, null if no match
     */
    private NumberSelectionResult findFirstMatchingRegex(List<String> numberQueries, Set<IncomingPhoneNumber> regexSet
    ) {
        NumberSelectionResult matchedRegex = new NumberSelectionResult(null, false, null);
        try {
            Iterator<IncomingPhoneNumber> iterator = regexSet.iterator();
            while (matchedRegex.getNumber() == null && iterator.hasNext()) {
                IncomingPhoneNumber currentRegex = iterator.next();
                String phoneRegexPattern = null;
                //here we perform string replacement to allow proper regex compilation
                if (currentRegex.getPhoneNumber().startsWith("+")) {
                    //ensures leading + sign is interpreted as expected char
                    phoneRegexPattern = currentRegex.getPhoneNumber().replace("+", "/+");
                } else if (currentRegex.getPhoneNumber().startsWith("*")) {
                    //ensures leading * sign is interpreted as expected char
                    phoneRegexPattern = currentRegex.getPhoneNumber().replace("*", "/*");
                } else {
                    phoneRegexPattern = currentRegex.getPhoneNumber();
                }
                Pattern p = Pattern.compile(phoneRegexPattern);
                int i = 0;
                //we evalute the current regex to the list of incoming numbers
                //we stop as soon as a match is found
                while (matchedRegex.getNumber() == null && i < numberQueries.size()) {
                    Matcher m = p.matcher(numberQueries.get(i));
                    if (m.find()) {
                        //match found, exit from loops and return
                        matchedRegex = new NumberSelectionResult(currentRegex, false, ResultType.REGEX);
                    } else if (logger.isInfoEnabled()) {
                        String msg = String.format("Regex \"%s\" cannot be matched for phone number \"%s\"", phoneRegexPattern, numberQueries.get(i));
                        logger.info(msg);
                    }
                    i = i + 1;
                }
            }
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                String msg = String.format("Exception while trying to match for a REGEX, exception: %s", e);
                logger.debug(msg);
            }
        }
        if (matchedRegex.getNumber() == null) {
            logger.info("No matching phone number found, make sure your Restcomm Regex phone number is correctly defined");
        }

        return matchedRegex;

    }
}

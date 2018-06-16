package org.restcomm.connect.core.service.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import javax.servlet.ServletContext;

import org.junit.Test;
import org.mockito.Mockito;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.api.ProfileService;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.OrganizationsDao;
import org.restcomm.connect.dao.ProfileAssociationsDao;
import org.restcomm.connect.dao.ProfilesDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.dao.entities.Profile;
import org.restcomm.connect.dao.entities.ProfileAssociation;

public class ProfileServiceTest {

    /**
     * case 1: given: a profile is explicitly assigned to an account
     * calling retrieveExplicitlyAssociatedProfile by account sid will return that profile
     * @throws SQLException
     */
    @Test
	public void retrieveExplicitlyAssociatedProfileCase1() throws SQLException {
    	MockingService mocks = new MockingService();
        Account account = returnValidAccount(mocks);
        Profile expectedProfile = returnProfile(mocks);
        returnProfileAssociation(new Sid(expectedProfile.getSid()), account.getSid(), mocks);
        Profile resultantProfile = mocks.profileService.retrieveExplicitlyAssociatedProfile(account.getSid());
        assertEquals(expectedProfile.getSid(), resultantProfile.getSid());
	}

    /**
     * case 2: given: a profile is explicitly assigned to an organization
     * calling retrieveExplicitlyAssociatedProfile by organization sid will return that profile
     * @throws SQLException
     */
    @Test
	public void retrieveExplicitlyAssociatedProfileCase2() throws SQLException {
    	MockingService mocks = new MockingService();
        Organization organization = returnValidOrganization(mocks);
        Profile expectedProfile = returnProfile(mocks);
        returnProfileAssociation(new Sid(expectedProfile.getSid()), organization.getSid(), mocks);
        Profile resultantProfile = mocks.profileService.retrieveExplicitlyAssociatedProfile(organization.getSid());
        assertEquals(expectedProfile.getSid(), resultantProfile.getSid());
	}

    /**
     * case 3: given: a profile is not explicitly assigned to an account
     * calling retrieveExplicitlyAssociatedProfile by account sid will return NULL
     * @throws SQLException
     */
    @Test
	public void retrieveExplicitlyAssociatedProfileCase3() throws SQLException {
    	MockingService mocks = new MockingService();
        Account account = returnValidAccount(mocks);
        Profile resultantProfile = mocks.profileService.retrieveExplicitlyAssociatedProfile(account.getSid());
        assertNull(resultantProfile);
	}

    /**
     * case 4: given: a profile is not explicitly assigned to an organization
     * calling retrieveExplicitlyAssociatedProfile by organization sid will return NULL
     * @throws SQLException
     */
    @Test
	public void retrieveExplicitlyAssociatedProfileCase4() throws SQLException {
    	MockingService mocks = new MockingService();
    	Organization organization = returnValidOrganization(mocks);
        Profile resultantProfile = mocks.profileService.retrieveExplicitlyAssociatedProfile(organization.getSid());
        assertNull(resultantProfile);
	}

    /**
     * case 1: given: a profile is explicitly assigned to an organization
     * calling retrieveEffectiveProfileByOrganizationSid by organization sid will return that profile
     * @throws SQLException 
     */
    @Test
	public void retrieveEffectiveProfileByOrganizationSidCase1() throws SQLException {
    	MockingService mocks = new MockingService();
    	Organization organization = returnValidOrganization(mocks);
    	Profile expectedProfile = returnProfile(mocks);
        returnProfileAssociation(new Sid(expectedProfile.getSid()), organization.getSid(), mocks);
        Profile resultantProfile = mocks.profileService.retrieveEffectiveProfileByOrganizationSid(organization.getSid());
        assertEquals(expectedProfile.getSid(), resultantProfile.getSid());
	}

    /**
     * case 2: given: a profile is NOT explicitly assigned to an organization
     * calling retrieveEffectiveProfileByOrganizationSid by organization sid will return DEFAULT profile
     * @throws SQLException 
     */
    @Test
	public void retrieveEffectiveProfileByOrganizationSidCase2() throws SQLException {
    	MockingService mocks = new MockingService();
    	Organization organization = returnValidOrganization(mocks);
    	returnDefaultProfile(mocks);
    	Profile resultantProfile = mocks.profileService.retrieveEffectiveProfileByOrganizationSid(organization.getSid());
        assertEquals(Profile.DEFAULT_PROFILE_SID, resultantProfile.getSid());
	}

    /**
     * case 1: given: a profile is explicitly assigned to an account
     * calling retrieveEffectiveProfileByAccountSid by account sid will return that profile
     * @throws SQLException 
     */
    @Test
	public void retrieveEffectiveProfileByAccountSidCase1() throws SQLException {
    	MockingService mocks = new MockingService();
    	Account account = returnValidAccount(mocks);
    	Profile expectedProfile = returnProfile(mocks);
        returnProfileAssociation(new Sid(expectedProfile.getSid()), account.getSid(), mocks);
        Profile resultantProfile = mocks.profileService.retrieveEffectiveProfileByAccountSid(account.getSid());
        assertEquals(expectedProfile.getSid(), resultantProfile.getSid());
	}

    /**
     * case 2: given: a profile is NOT explicitly assigned to an account, but its associated to its parent
     * calling retrieveEffectiveProfileByAccountSid by account sid will return that profile
     * @throws SQLException 
     */
    @Test
	public void retrieveEffectiveProfileByAccountSidCase2() throws SQLException {
    	MockingService mocks = new MockingService();
    	Profile expectedProfile = returnProfile(mocks);
    	Account account = returnValidAccountWithOnlyParentAssignedProfile(new Sid(expectedProfile.getSid()), mocks);
        Profile resultantProfile = mocks.profileService.retrieveEffectiveProfileByAccountSid(account.getSid());
        assertEquals(expectedProfile.getSid(), resultantProfile.getSid());
	}

    /**
     * case 3: given: a profile is NOT explicitly assigned to an account nor to its parent, but its associated to its grand parent
     * calling retrieveEffectiveProfileByAccountSid by account sid will return that profile
     * @throws SQLException 
     */
    @Test
	public void retrieveEffectiveProfileByAccountSidCase3() throws SQLException {
    	MockingService mocks = new MockingService();
    	Profile expectedProfile = returnProfile(mocks);
    	Account account = returnValidAccountWithOnlyGrandParentAssignedProfile(new Sid(expectedProfile.getSid()), mocks);
        Profile resultantProfile = mocks.profileService.retrieveEffectiveProfileByAccountSid(account.getSid());
        assertEquals(expectedProfile.getSid(), resultantProfile.getSid());
	}

    /**
     * case 4: given: a profile is NOT explicitly assigned to an account nor to its parent or grand parent, but its associated to its organization
     * calling retrieveEffectiveProfileByAccountSid by account sid SHOULD return that profile
     * @throws SQLException 
     */
    @Test
	public void retrieveEffectiveProfileByAccountSidCase4() throws SQLException {
    	MockingService mocks = new MockingService();
    	Profile expectedProfile = returnProfile(mocks);
    	Account account = returnValidAccountWitOrganizationAssignedProfile(new Sid(expectedProfile.getSid()), mocks);
    	Profile resultantProfile = mocks.profileService.retrieveEffectiveProfileByAccountSid(account.getSid());
        assertEquals(expectedProfile.getSid(), resultantProfile.getSid());
	}

    /**
     * case 5: given: a profile is NOT explicitly assigned to an account nor to its parent, grand parent or organization:
     * calling retrieveEffectiveProfileByAccountSid by account sid SHOULD return DEFAULT profile
     * @throws SQLException 
     */
    @Test
	public void retrieveEffectiveProfileByAccountSidCase5() throws SQLException {
    	MockingService mocks = new MockingService();
    	Account account = returnValidAccount(mocks);
    	returnDefaultProfile(mocks);
        Profile resultantProfile = mocks.profileService.retrieveEffectiveProfileByAccountSid(account.getSid());
        assertEquals(Profile.DEFAULT_PROFILE_SID, resultantProfile.getSid());
	}

    /**
     * case 6: 
     * </p>given 1: an account X is child of account Y.
     * </p>given 2: X belongs to organization XO.
     * </p>given 3: Y belongs to organization YO. (parent organization is different than child organization)
     * </p>given 4: profile A is associated to organization XO
     * </p>given 5: profile B is associated to organization YO
     * </p>When retrieveEffectiveProfileByAccountSid (X) should return A.
     * </p>calling retrieveEffectiveProfileByAccountSid by account sid SHOULD return that profile
     * @throws SQLException 
     */
    @Test
	public void retrieveEffectiveProfileByAccountSidCase6() throws SQLException {
    	
    	MockingService mocks = new MockingService();
    	Profile expectedParentOrganizationProfile = returnProfile(mocks);
    	Profile expectedChildOrganizationProfile = returnProfile(mocks);
    	
    	Sid parentOrganizationSid = Sid.generate(Sid.Type.ORGANIZATION);
    	Sid childOrganizationSid = Sid.generate(Sid.Type.ORGANIZATION);
    	
        Sid acctSidParent = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builderParent = Account.builder();
        builderParent.setSid(acctSidParent);
        builderParent.setOrganizationSid(parentOrganizationSid);
        Account accountParent = builderParent.build();
        
        Sid acctSid = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builder = Account.builder();
        builder.setSid(acctSid);
        builder.setOrganizationSid(childOrganizationSid);
        builder.setParentSid(acctSidParent);
        Account account = builder.build();

        when (mocks.mockedAccountsDao.getAccount(acctSidParent)).thenReturn(accountParent);
        when (mocks.mockedAccountsDao.getAccount(acctSid)).thenReturn(account);
        returnProfileAssociation(new Sid(expectedParentOrganizationProfile.getSid()), parentOrganizationSid, mocks);
        returnProfileAssociation(new Sid(expectedChildOrganizationProfile.getSid()), childOrganizationSid, mocks);
        
    	Profile resultantProfile = mocks.profileService.retrieveEffectiveProfileByAccountSid(account.getSid());
        assertEquals(expectedChildOrganizationProfile.getSid(), resultantProfile.getSid());
	}

    private Account returnValidAccount(MockingService mocks ) {
    	Sid organizationSid = Sid.generate(Sid.Type.ORGANIZATION);
    	
        Sid acctSidGrandParent = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builderGParent = Account.builder();
        builderGParent.setSid(acctSidGrandParent);
        builderGParent.setOrganizationSid(organizationSid);
        Account accountGrandParent = builderGParent.build();
    	
        Sid acctSidParent = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builderParent = Account.builder();
        builderParent.setSid(acctSidParent);
        builderParent.setOrganizationSid(organizationSid);
        builderParent.setParentSid(acctSidGrandParent);
        Account accountParent = builderParent.build();
        
        Sid acctSid = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builder = Account.builder();
        builder.setSid(acctSid);
        builder.setOrganizationSid(organizationSid);
        builder.setParentSid(acctSidParent);
        Account account = builder.build();

        when (mocks.mockedAccountsDao.getAccount(acctSidParent)).thenReturn(accountParent);
        when (mocks.mockedAccountsDao.getAccount(acctSidGrandParent)).thenReturn(accountGrandParent);
        when (mocks.mockedAccountsDao.getAccount(acctSid)).thenReturn(account);

        return account;
    }

    private Account returnValidAccountWitOrganizationAssignedProfile(Sid profileSid, MockingService mocks ) throws SQLException {
    	Sid organizationSid = Sid.generate(Sid.Type.ORGANIZATION);
    	Sid acctSidGrandParent = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builderGParent = Account.builder();
        builderGParent.setSid(acctSidGrandParent);
        builderGParent.setOrganizationSid(organizationSid);
        Account accountGrandParent = builderGParent.build();
    	
        Sid acctSidParent = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builderParent = Account.builder();
        builderParent.setSid(acctSidParent);
        builderParent.setOrganizationSid(organizationSid);
        builderParent.setParentSid(acctSidGrandParent);
        Account accountParent = builderParent.build();
        
        Sid acctSid = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builder = Account.builder();
        builder.setSid(acctSid);
        builder.setOrganizationSid(organizationSid);
        builder.setParentSid(acctSidParent);
        Account account = builder.build();

        when (mocks.mockedAccountsDao.getAccount(acctSidGrandParent)).thenReturn(accountGrandParent);
        when (mocks.mockedAccountsDao.getAccount(acctSidParent)).thenReturn(accountParent);
        when (mocks.mockedAccountsDao.getAccount(acctSid)).thenReturn(account);
        returnProfileAssociation(profileSid, organizationSid, mocks);

        return account;
    }

    private Account returnValidAccountWithOnlyGrandParentAssignedProfile(Sid profileSid, MockingService mocks ) throws SQLException {
    	Sid organizationSid = Sid.generate(Sid.Type.ORGANIZATION);
    	Sid acctSidGrandParent = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builderGParent = Account.builder();
        builderGParent.setSid(acctSidGrandParent);
        builderGParent.setOrganizationSid(organizationSid);
        Account accountGrandParent = builderGParent.build();
    	
        Sid acctSidParent = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builderParent = Account.builder();
        builderParent.setSid(acctSidParent);
        builderParent.setOrganizationSid(organizationSid);
        builderParent.setParentSid(acctSidGrandParent);
        Account accountParent = builderParent.build();
        
        Sid acctSid = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builder = Account.builder();
        builder.setSid(acctSid);
        builder.setOrganizationSid(organizationSid);
        builder.setParentSid(acctSidParent);
        Account account = builder.build();

        when (mocks.mockedAccountsDao.getAccount(acctSidParent)).thenReturn(accountParent);
        when (mocks.mockedAccountsDao.getAccount(acctSidGrandParent)).thenReturn(accountGrandParent);
        when (mocks.mockedAccountsDao.getAccount(acctSid)).thenReturn(account);
        returnProfileAssociation(profileSid, acctSidGrandParent, mocks);

        return account;
    }

    private Account returnValidAccountWithOnlyParentAssignedProfile(Sid profileSid, MockingService mocks ) throws SQLException {
    	Sid organizationSid = Sid.generate(Sid.Type.ORGANIZATION);
    	Sid acctSidParent = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builderParent = Account.builder();
        builderParent.setSid(acctSidParent);
        builderParent.setOrganizationSid(organizationSid);
        Account accountParent = builderParent.build();
        
        Sid acctSid = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builder = Account.builder();
        builder.setSid(acctSid);
        builder.setOrganizationSid(organizationSid);
        builder.setParentSid(acctSidParent);
        Account account = builder.build();

        when (mocks.mockedAccountsDao.getAccount(acctSidParent)).thenReturn(accountParent);
        when (mocks.mockedAccountsDao.getAccount(acctSid)).thenReturn(account);
        returnProfileAssociation(profileSid, acctSidParent, mocks);

        return account;
    }

    private Organization returnValidOrganization(MockingService mocks ) {
        Sid organizationSid = Sid.generate(Sid.Type.ORGANIZATION);
        Organization.Builder builder = Organization.builder();
        builder.setSid(organizationSid);
        builder.setDomainName("test");
        Organization organization = builder.build();
        return organization;
    }

    private ProfileAssociation returnProfileAssociation(Sid profileSid, Sid targetSid, MockingService mocks ) throws SQLException {
        ProfileAssociation assoc = new ProfileAssociation(profileSid, targetSid, null, null);
        when (mocks.mockedProfileAssociationsDao.getProfileAssociationByTargetSid(targetSid.toString())).
                thenReturn(assoc);
        return assoc;
    }

    private Profile returnDefaultProfile(MockingService mocks ) throws SQLException {
        Profile profile = new Profile(Profile.DEFAULT_PROFILE_SID, "{}", null, null);
        when (mocks.mockedProfilesDao.getProfile(profile.getSid())).
                thenReturn(profile);
        return profile;
    }

    private Profile returnProfile(MockingService mocks ) throws SQLException {
        Profile profile = new Profile(Sid.generate(Sid.Type.PROFILE).toString(), "{}", null, null);
        when (mocks.mockedProfilesDao.getProfile(profile.getSid())).thenReturn(profile);
        return profile;

    }

    class MockingService {

        ServletContext mockedServletContext = Mockito.mock(ServletContext.class);
        DaoManager mockedDaoManager = Mockito.mock(DaoManager.class);
        OrganizationsDao mockedOrganizationsDao = Mockito.mock(OrganizationsDao.class);
        AccountsDao mockedAccountsDao = Mockito.mock(AccountsDao.class);
        ProfilesDao mockedProfilesDao = Mockito.mock(ProfilesDao.class);
        ProfileAssociationsDao mockedProfileAssociationsDao = Mockito.mock(ProfileAssociationsDao.class);
        ProfileService profileService;
        
        public MockingService() {
            when(mockedServletContext.getAttribute(DaoManager.class.getName())).
                    thenReturn(mockedDaoManager);
            when(mockedDaoManager.getProfileAssociationsDao()).thenReturn(mockedProfileAssociationsDao);
            when(mockedDaoManager.getProfilesDao()).thenReturn(mockedProfilesDao);
            when(mockedDaoManager.getAccountsDao()).thenReturn(mockedAccountsDao);
            when(mockedDaoManager.getAccountsDao()).thenReturn(mockedAccountsDao);
            profileService = new ProfileServiceImpl(mockedDaoManager);
        }
    }
}

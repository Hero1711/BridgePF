package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent2;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormPathUserAdminServiceTest {

    // Decided not to use the helper class for this test because so many edge conditions are 
    // being tested here.
    
    @Resource
    AuthenticationServiceImpl authService;

    @Resource
    StormPathUserAdminService service;

    @Resource
    BridgeConfig bridgeConfig;
    
    @Resource
    StudyServiceImpl studyService;
    
    @Resource
    StormPathUserAdminService userAdminService;
    
    private Study study;
    
    private SignUp signUp;
    
    private User test2User;
    private User test3User;
    
    @BeforeClass
    public static void initialSetUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @AfterClass
    public static void finalCleanUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @Before
    public void before() {
        study = studyService.getStudyByIdentifier(TEST_STUDY_IDENTIFIER);
        signUp = new SignUp(bridgeConfig.getUser() + "-admin", "admin@sagebridge.org", "P4ssword");
        
        SignIn signIn = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        authService.signIn(study, signIn).getUser();
    }

    @After
    public void after() {
        if (test2User != null) {
            userAdminService.deleteUser(test2User);
            test2User = null;
        }
        if (test3User != null) {
            userAdminService.deleteUser(test3User);
            test3User = null;
        }
    }

    @Test
    public void canCreateUserIdempotently() {
        test2User = service.createUser(signUp, study, true, true).getUser();
        test2User = service.createUser(signUp, study, true, true).getUser();

        assertEquals("Correct email", signUp.getEmail(), test2User.getEmail());
        assertEquals("Correct username", signUp.getUsername(), test2User.getUsername());
        assertTrue("Has consented", test2User.doesConsent());
    }

    @Test(expected = BridgeServiceException.class)
    public void deletedUserHasBeenDeleted() {
        test2User = service.createUser(signUp, study, true, true).getUser();
        
        service.deleteUser(test2User);
        
        // This should fail with a 404.
        authService.signIn(study, new SignIn(signUp.getEmail(), signUp.getPassword()));
    }

    @Test
    public void canCreateUserWithoutConsentingOrSigningUserIn() {
        UserSession session1 = service.createUser(signUp, study, false, false);
        assertNull("No session", session1);
        
        try {
            authService.signIn(study, new SignIn(signUp.getEmail(), signUp.getPassword()));
            fail("Should throw a consent required exception");
        } catch (ConsentRequiredException e) {
            test2User = e.getUserSession().getUser();
        }
    }
}

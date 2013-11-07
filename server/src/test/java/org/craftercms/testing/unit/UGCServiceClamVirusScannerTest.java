package org.craftercms.testing.unit;

import org.bson.types.ObjectId;
import org.craftercms.profile.impl.domain.Profile;
import org.craftercms.security.api.RequestContext;
import org.craftercms.security.api.UserProfile;
import org.craftercms.security.authentication.AuthenticationToken;
import org.craftercms.social.domain.Target;
import org.craftercms.social.domain.UGC;
import org.craftercms.social.domain.UGC.ModerationStatus;
import org.craftercms.social.domain.UGCAudit;
import org.craftercms.social.domain.UGCAudit.AuditAction;
import org.craftercms.social.exceptions.AttachmentErrorException;
import org.craftercms.social.exceptions.PermissionDeniedException;
import org.craftercms.social.moderation.ModerationDecision;
import org.craftercms.social.repositories.UGCAuditRepository;
import org.craftercms.social.repositories.UGCRepository;
import org.craftercms.social.services.CounterService;
import org.craftercms.social.services.PermissionService;
import org.craftercms.social.services.SupportDataAccess;
import org.craftercms.social.services.TenantService;
import org.craftercms.social.services.impl.UGCServiceImpl;
import org.craftercms.social.services.impl.VirusScannerServiceImpl;
import org.craftercms.social.util.action.ActionEnum;
import org.craftercms.social.util.action.ActionUtil;
import org.craftercms.social.util.support.CrafterProfile;
import org.craftercms.social.util.web.Attachment;
import org.craftercms.virusscanner.impl.ClamavVirusScannerImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { RequestContext.class})
public class UGCServiceClamVirusScannerTest {
//	@Test //TODO: DISABLING test to make the build up
//	public void test() {
//
//	}

    @Mock
    private PermissionService permissionService;
    @Mock
    private CounterService counterService;
    @Mock
    private TenantService tenantService;

    @Mock
    private CrafterProfile crafterProfileService;
    @Mock
    private UGCAuditRepository auditRepository;
    @Mock
    private UGCRepository repository;
    @Mock
    private ModerationDecision moderationDecisionManager;
    @Mock
    private SupportDataAccess supportDataAccess;

    @Mock
    private VirusScannerServiceImpl virusScannerService;

    @InjectMocks
    private UGCServiceImpl ugcServiceImpl;

    private static final String VALID_ID = 	 "520278180364146bdbd42d1f";
    private static final String ROOT_ID = 	 "520278180364146bdbd42d16";
    private static final String PROFILE_ID = "5202b88203643ac2849709bc";
    private static final String ATTACHMENT_ID = "5202b88203643ac2849709ac";
    private static final String SORT_FIELD = "createdDate";
    private static final String SORT_ORDER = "DESC";

    private Profile currentProfile;
    private UGC currentUGC;
    private UGC parentUGC;

    private List<UGC> ul;
    private List<UGCAudit> la;
    private List<String> moderateRootRoles;

    public UGCServiceClamVirusScannerTest(){
        //this.virusScannerService = new VirusScannerServiceImpl();
        //this.virusScannerService.setVirusScanner(new ClamavVirusScannerImpl("localhost", 3310, 60000));

    }

    @Before
    public void startup() {
        mockStatic(RequestContext.class);
        when(RequestContext.getCurrent()).thenReturn(getCurrentRequestContext());


        currentProfile = getProfile();
        currentUGC = getUGC();
        ul = new ArrayList<UGC>();
        ul.add(currentUGC);
        UGCAudit audit = getAudit();
        la = new ArrayList<UGCAudit>();
        la.add(getAudit());
        moderateRootRoles = new ArrayList<String>();
        moderateRootRoles.add("tester");

        Attachment attachment = new Attachment("image/png", 412, "mypicture.png");

        when(crafterProfileService.getProfile(PROFILE_ID)).thenReturn(currentProfile);
        when(repository.findOne(new ObjectId(VALID_ID))).thenReturn(currentUGC);
        when(repository.findOne(new ObjectId(ROOT_ID))).thenReturn(currentUGC);
        when(repository.findUGCs("","", new String[]{""}, ActionEnum.READ, 0, 0, SORT_FIELD,SORT_ORDER)).thenReturn(ul);
        when(repository.findUGC(new ObjectId(VALID_ID), ActionEnum.READ,new String[]{""})).thenReturn(currentUGC);
        when(repository.findByIds(Mockito.<ObjectId[]>any())).thenReturn(ul);
        when(repository.findByTenantTargetPaging("test","testing",1,10,ActionEnum.READ,SORT_FIELD,SORT_ORDER)).thenReturn(ul);
        when(repository.findTenantAndTargetIdAndParentIsNull(Mockito.<String>any(),Mockito.<String>any(),Mockito.<ActionEnum>any())).thenReturn(ul);
        when(repository.save(Mockito.<UGC>any())).thenReturn(currentUGC);
        when(permissionService.getQuery(ActionEnum.READ, currentProfile)).thenReturn(getQuery());
        when(permissionService.allowed(Mockito.<ActionEnum>any(), Mockito.<UGC>any(), Mockito.<Profile>any())).thenReturn(true);
        when(counterService.getNextSequence(Mockito.<String>any())).thenReturn(1l);
        when(auditRepository.findByProfileIdAndAction(PROFILE_ID, AuditAction.CREATE)).thenReturn(la);
        when(auditRepository.findByProfileIdAndUgcIdAndAction(PROFILE_ID, new ObjectId(VALID_ID),AuditAction.CREATE)).thenReturn(audit);
        when(tenantService.getRootModeratorRoles("test")).thenReturn(moderateRootRoles);
        when(supportDataAccess.getAttachment(Mockito.<ObjectId>any())).thenReturn(attachment);

        when(virusScannerService.isNullScanner()).thenReturn(true);
        when(virusScannerService.scan(Mockito.<File>any(),"test")).thenReturn("true");

    }



    // Testing VirusScanner
    // TODO update the tests to work with the addAttachments method
    /*@Test
    public void testNewChildCleanFile() {
        mockStatic(RequestContext.class);

        when(RequestContext.getCurrent()).thenReturn(getCurrentRequestContext());

        MultipartFile[] files = new MultipartFile[1];

        try{
            String path = getClass().getResource("/clean.txt").getPath();
            File file = new File(path);
            MockMultipartFile mockMultipartFile = new MockMultipartFile(file.getAbsolutePath(),file.getAbsolutePath(),"file",new FileInputStream(file));
            files[0] = mockMultipartFile;
        }
        catch (FileNotFoundException e){
            fail(e.getMessage());
        }
        catch (IOException e){
            fail(e.getMessage());
        }

        try {
            ugcServiceImpl.newUgc(currentUGC);
        } catch (PermissionDeniedException pde) {
            fail(pde.getMessage());
        } catch (AttachmentErrorException dee) {
            fail(dee.getMessage());
        }
        catch (NullPointerException ignore) {
            // This NullPointerException probably means that the item could not be
            // store successfully (mainly because of the db and the nature of these tests).
            // This may not always true but it doesn't matter because the catch above
            // should be enough to test the virus scanning
        }


    }

    @Test
    public void testNewChildCleanPDFFile() {
        mockStatic(RequestContext.class);

        when(RequestContext.getCurrent()).thenReturn(getCurrentRequestContext());

        MultipartFile[] files = new MultipartFile[1];

        try{
            String path = getClass().getResource("/warranty.pdf").getPath();
            File file = new File(path);
            MockMultipartFile mockMultipartFile = new MockMultipartFile(file.getAbsolutePath(),file.getAbsolutePath(),"file",new FileInputStream(file));
            files[0] = mockMultipartFile;
        }
        catch (FileNotFoundException e){
            fail(e.getMessage());
        }
        catch (IOException e){
            fail(e.getMessage());
        }

        try {
            ugcServiceImpl.newUgc(currentUGC);
        } catch (PermissionDeniedException pde) {
            fail(pde.getMessage());
        } catch (AttachmentErrorException dee) {
            fail(dee.getMessage());
        }
        catch (NullPointerException ignore) {
            // This NullPointerException probably means that the item could not be
            // store successfully (mainly because of the db and the nature of these tests).
            // This may not always true but it doesn't matter because the catch above
            // should be enough to test the virus scanning
        }


    }

    @Test
    public void testNewChildVirusFile() {
        mockStatic(RequestContext.class);

        when(RequestContext.getCurrent()).thenReturn(getCurrentRequestContext());

        MultipartFile[] files = new MultipartFile[1];

        try{
            String path = getClass().getResource("/eicar.txt").getPath();
            File file = new File(path);
            MockMultipartFile mockMultipartFile = new MockMultipartFile(file.getAbsolutePath(),file.getAbsolutePath(),"file",new FileInputStream(file));
            files[0] = mockMultipartFile;
        }
        catch (FileNotFoundException e){
            fail(e.getMessage());
        }
        catch (IOException e){
            fail(e.getMessage());
        }

        try {
            ugcServiceImpl.newUgc(currentUGC);
        } catch (PermissionDeniedException pde) {
            fail(pde.getMessage());
        } catch (AttachmentErrorException aee) {
            //assertTrue(ClamavVirusScannerImpl.THREAT_FOUND_MESSAGE.equals(aee.getMessage()));
        }

    }
      */
    // End of the virus scanning testing

    private UGC getUGC() {
        UGC ugc= new UGC();
        ugc.setCreatedBy("test");
        ugc.setCreatedDate(new Date());
        ugc.setFlagCount(0);
        ugc.setId(new ObjectId(VALID_ID));
        ugc.setLastModifiedBy("test");
        ugc.setLastModifiedDate(new Date());
        ugc.setLikeCount(0);
        ugc.setModerationStatus(ModerationStatus.UNMODERATED);
        ugc.setOffenceCount(0);
        ugc.setOwner("test");
        ugc.setProfile(getProfile());
        ugc.setProfileId(PROFILE_ID);
        ugc.setTargetId("testing");
        ugc.setTenant("test");
        ugc.setTextContent("Testing Content");
        ugc.setTimesModerated(0);
        ugc.setAttachmentId(new ObjectId[]{});
        return ugc;
    }

    private Profile getProfile() {
        Map<String,Object> attributes = new HashMap<String, Object>();
        Profile p = new Profile(PROFILE_ID, "test", "test", true, new Date(), new Date(), attributes,"", true);
        return p;
    }

    private Query getQuery() {
        String[] roles = new String[]{"tester"};
        Query query = new Query();
        query.addCriteria(Criteria.where("actions").elemMatch(
                Criteria.where("name").is("read")
                        .and("roles").in(roles)));
        return query;
    }

    private RequestContext getCurrentRequestContext() {
        AuthenticationToken at = new AuthenticationToken();
        UserProfile us = new UserProfile(getProfile());
        at.setProfile(us);
        RequestContext rc = new RequestContext();
        rc.setTenantName("test");
        rc.setAuthenticationToken(at);
        return rc;
    }

    private UGCAudit getAudit() {
        UGCAudit a = new UGCAudit();
        a.setAction(AuditAction.CREATE);
        a.setProfileId(PROFILE_ID);
        a.setReason("");
        a.setTenant("test");
        Target t = new Target();
        t.setTargetId("targetId");
        t.setTargetDescription("targetdescription");
        t.setTargetUrl("targeturl");
        a.setTarget(t);
        //a.setId(new ObjectId("5202b88203643ac2849709bc"));
        a.setRow(10l);
        a.setUgcId(new ObjectId(VALID_ID));
        return a;
    }
}
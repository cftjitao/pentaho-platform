package org.pentaho.platform.web.http.api.resources.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.pentaho.platform.api.engine.IAuthorizationPolicy;
import org.pentaho.platform.api.repository2.unified.IRepositoryFileData;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.api.repository2.unified.RepositoryFileAcl;
import org.pentaho.platform.api.repository2.unified.data.simple.SimpleRepositoryFileData;
import org.pentaho.platform.repository.RepositoryDownloadWhitelist;
import org.pentaho.platform.repository2.unified.fileio.RepositoryFileInputStream;
import org.pentaho.platform.repository2.unified.fileio.RepositoryFileOutputStream;
import org.pentaho.platform.repository2.unified.webservices.DefaultUnifiedRepositoryWebService;
import org.pentaho.platform.repository2.unified.webservices.RepositoryFileAclDto;
import org.pentaho.platform.repository2.unified.webservices.RepositoryFileDto;

public class FileServiceTest {

  private static FileService fileService;

  @Before
  public void setUp() {
    fileService = spy( new FileService() );
    fileService.defaultUnifiedRepositoryWebService = mock( DefaultUnifiedRepositoryWebService.class );
    fileService.repository = mock( IUnifiedRepository.class );
    fileService.policy = mock( IAuthorizationPolicy.class );
  }

  @After
  public void cleanup() {
    fileService = null;
  }

  @Test
  public void testDoDeleteFiles() throws Exception {
    String params = "file1,file2";

    fileService.doDeleteFiles( params );

    verify( fileService.getRepoWs(), times( 1 ) ).deleteFile( "file1", null );
    verify( fileService.getRepoWs(), times( 1 ) ).deleteFile( "file2", null );
  }

  @Test
  public void testDoDeleteFilesException() {
    String params = "file1,file2";
    doThrow( new IllegalArgumentException() ).when(
        fileService.defaultUnifiedRepositoryWebService ).deleteFile( anyString(), anyString() );

    try {
      fileService.doDeleteFiles( params );
      fail(); //This line should never be reached
    } catch ( IllegalArgumentException e ) {
      //Expected exception
    } catch ( Exception e ) {
      fail();
    }
  }

  @Test
  public void testDoCopyFiles() throws Exception {

    String destinationPath = "/path/to/destination";
    String destinationPathColon = ":path:to:destination";

    String filePath1 = "/path/to/source/file1.ext";
    String fileId1 = "file1";

    String filePath2 = "/path/to/source/file2.ext";
    String fileId2 = "file2";

    when( fileService.policy.isAllowed( anyString() ) ).thenReturn( true );

    RepositoryFile repositoryFile = mock( RepositoryFile.class );
    when( fileService.repository.createFile( any( Serializable.class ), any( RepositoryFile.class ), any(
        IRepositoryFileData.class ), any( RepositoryFileAcl.class ), anyString() ) ).thenReturn( repositoryFile );

    RepositoryFile destDir = mock( RepositoryFile.class );
    when( destDir.isFolder() ).thenReturn( true );
    when( destDir.getPath() ).thenReturn( destinationPath );
    when( fileService.repository.getFile( destinationPath ) ).thenReturn( destDir );

    RepositoryFile repositoryFile1 = mock( RepositoryFile.class );
    when( repositoryFile1.isFolder() ).thenReturn( false );
    when( repositoryFile1.getPath() ).thenReturn( filePath1 );
    when( fileService.repository.getFileById( fileId1 ) ).thenReturn( repositoryFile1 );

    RepositoryFile repositoryFile2 = mock( RepositoryFile.class );
    when( repositoryFile2.isFolder() ).thenReturn( false );
    when( repositoryFile2.getPath() ).thenReturn( filePath2 );
    when( fileService.repository.getFileById( fileId2 ) ).thenReturn( repositoryFile2 );

    fileService.doCopyFiles( destinationPathColon, 1, fileId1 + "," + fileId2 );

    verify( fileService.repository, times( 2 ) )
        .createFile( any( Serializable.class ), any( RepositoryFile.class ), any(
            IRepositoryFileData.class ), any( RepositoryFileAcl.class ), anyString() );
    verify( fileService.repository ).getFile( destinationPath );
    verify( fileService.repository ).getFileById( fileId1 );
    verify( fileService.repository ).getFileById( fileId2 );
  }

  @Test
  public void doCopyFilesException() throws Exception {

    String destinationPath = "/path/to/destination";
    String destinationPathColon = ":path:to:destination";

    String filePath1 = "/path/to/source/file1.ext";
    String fileId1 = "file1";

    String filePath2 = "/path/to/source/file2.ext";
    String fileId2 = "file2";

    when( fileService.policy.isAllowed( anyString() ) ).thenReturn( false );

    try {
      fileService.doCopyFiles( destinationPathColon, 1, fileId1 + "," + fileId2 );
      fail();
    } catch ( IllegalArgumentException e ) {
      //Should catch the exception
    }
  }

  @Test
  public void testDoGetFileOrDir() throws Exception {
    RepositoryFile file = mock( RepositoryFile.class );
    doReturn( "file.txt" ).when( file ).getName();

    when( fileService.repository.getFile( anyString() ) ).thenReturn( file );

    RepositoryFileInputStream mockInputStream = mock( RepositoryFileInputStream.class );

    doReturn( 1 ).when( fileService ).copy( any( java.io.InputStream.class ), any( java.io.OutputStream.class ) );
    doReturn( mockInputStream ).when( fileService ).getRepositoryFileInputStream( any( RepositoryFile.class ) );

    String pathId = "/usr/folder/file.txt";
    FileService.RepositoryFileToStreamWrapper wrapper = fileService.doGetFileOrDir( pathId );

    Assert.assertTrue( wrapper.getRepositoryFile().getName().compareTo( "file.txt" ) == 0 );
  }

  @Test
  public void testDoGetFileOrDirException() throws Exception {
    RepositoryFile file = mock( RepositoryFile.class );
    doReturn( "file.txt" ).when( file ).getName();

    RepositoryFileInputStream mockInputStream = mock( RepositoryFileInputStream.class );

    doReturn( 1 ).when( fileService ).copy( any( java.io.InputStream.class ), any( java.io.OutputStream.class ) );
    doReturn( mockInputStream ).when( fileService ).getRepositoryFileInputStream( any( RepositoryFile.class ) );

    String pathId = "/usr/folder/file.txt";
    try {
      fileService.doGetFileOrDir( pathId );
      fail(); //This line should never be reached
    } catch ( FileNotFoundException fileNotFound ) {
      //Expected exception
    }
  }

  @Test
  public void testSetFileAcls() throws Exception {
    RepositoryFileDto file = mock( RepositoryFileDto.class );
    doReturn( "file.txt" ).when( file ).getName();
    when( fileService.defaultUnifiedRepositoryWebService.getFile( anyString() ) ).thenReturn( file );

    String pathId = "/usr/folder/file.txt";
    RepositoryFileAclDto acl = mock( RepositoryFileAclDto.class );
    fileService.setFileAcls( pathId, acl );

    verify( acl, times( 1 ) ).setId( anyString() );
    verify( file, times( 1 ) ).getId();
    verify( fileService.defaultUnifiedRepositoryWebService, times( 1 ) ).updateAcl( acl );
  }

  @Test
  public void testSetFileAclsException() throws Exception {
    String pathId = "/usr/folder/file.txt";
    RepositoryFileAclDto acl = mock( RepositoryFileAclDto.class );
    try {
      fileService.setFileAcls( pathId, acl );
      fail();
    } catch ( FileNotFoundException e ) {
      //expected exception
    }
  }

  @Ignore
  public void testDoCreateFile() throws Exception {
    RepositoryFileOutputStream mockOutputStream = mock( RepositoryFileOutputStream.class );
    doReturn( mockOutputStream ).when( fileService ).getRepositoryFileOutputStream( anyString() );

    HttpServletRequest mockRequest = mock( HttpServletRequest.class );
    InputStream mockInputStream = mock( InputStream.class );

    doReturn( 1 ).when( fileService ).copy( mockInputStream, mockOutputStream );

    fileService.createFile( mockRequest, "testString", mockInputStream );

    verify( mockOutputStream, times( 1 ) ).setCharsetName( anyString() );
    verify( mockOutputStream, times( 1 ) ).close();
    verify( mockInputStream, times( 1 ) ).close();
  }

  @Ignore
  public void testDoCreateFileException() {
    RepositoryFileOutputStream mockOutputStream = mock( RepositoryFileOutputStream.class );
    doThrow( new IllegalArgumentException() ).when( fileService ).idToPath( anyString() );

    try {
      fileService.createFile( null, null, null );
      fail();
    } catch ( IllegalArgumentException e ) {
      // expected
    } catch ( Exception e ) {
      fail();
    }
  }

  @Test
  public void testDoDeleteFilesPermanent() throws Exception {
    String params = "file1,file2";

    fileService.doDeleteFilesPermanent( params );

    verify( fileService.getRepoWs(), times( 1 ) ).deleteFileWithPermanentFlag( "file1", true, null );
    verify( fileService.getRepoWs(), times( 1 ) ).deleteFileWithPermanentFlag( "file2", true, null );
  }

  @Test
  public void testDoDeleteLocale() throws Exception {
    RepositoryFileDto file = mock( RepositoryFileDto.class );
    doReturn( file ).when( fileService.defaultUnifiedRepositoryWebService ).getFile( anyString() );
    doReturn( "file.txt" ).when( file ).getId();
    fileService.doDeleteLocale( file.getId(), "en_US" );
    verify( fileService.getRepoWs(), times( 1 ) ).deleteLocalePropertiesForFile( "file.txt", "en_US" );
  }  
  
  @Test
  public void testDoDeleteFilesPermanentException() {
    String params = "file1,file2";
    doThrow( new IllegalArgumentException() ).when(
        fileService.defaultUnifiedRepositoryWebService ).deleteFileWithPermanentFlag( anyString(), eq( true ),
        anyString() );

    try {
      fileService.doDeleteFilesPermanent( params );
      fail(); //This line should never be reached
    } catch ( Exception e ) {
      //Expected exception
    }
  }

  @Test
  public void testDoMoveFiles() throws Exception {
    String destPathId = "/test";
    String[] params = { "file1", "file2" };

    RepositoryFileDto repositoryFileDto = mock( RepositoryFileDto.class );
    doReturn( destPathId ).when( repositoryFileDto ).getPath();

    doReturn( repositoryFileDto ).when( fileService.defaultUnifiedRepositoryWebService ).getFile( destPathId );

    Assert.assertTrue( fileService.doMoveFiles( destPathId, StringUtils.join( params, "," ) ) );

    verify( fileService.getRepoWs(), times( 1 ) ).moveFile( params[0], destPathId, null );
    verify( fileService.getRepoWs(), times( 1 ) ).moveFile( params[1], destPathId, null );
  }

  @Test
  public void testDoMoveFilesForUnknownDestPath() throws Exception {
    String destPathId = "/test";
    String[] params = { "file1", "file2" };

    doReturn( null ).when( fileService.defaultUnifiedRepositoryWebService ).getFile( destPathId );

    Assert.assertFalse( fileService.doMoveFiles( destPathId, StringUtils.join( params, "," ) ) );
    verify( fileService.getRepoWs(), times( 0 ) ).moveFile( params[0], destPathId, null );
    verify( fileService.getRepoWs(), times( 0 ) ).moveFile( params[1], destPathId, null );
  }

  @Test
  public void testDoMoveFilesException() throws Exception {
    String destPathId = "/test";
    String[] params = { "file1", "file2" };

    RepositoryFileDto repositoryFileDto = mock( RepositoryFileDto.class );
    doReturn( destPathId ).when( repositoryFileDto ).getPath();

    doReturn( repositoryFileDto ).when( fileService.defaultUnifiedRepositoryWebService ).getFile( destPathId );
    doThrow( new IllegalArgumentException() ).when( fileService.defaultUnifiedRepositoryWebService )
        .moveFile( params[0], destPathId, null );

    try {
      fileService.doMoveFiles( destPathId, StringUtils.join( params, "," ) );
      fail(); //This line should never be reached
    } catch ( Exception e ) {
      verify( fileService.getRepoWs(), times( 1 ) ).moveFile( params[0], destPathId, null );
      verify( fileService.getRepoWs(), times( 0 ) ).moveFile( params[1], destPathId, null );
    }
  }

  @Test
  public void testDoRestoreFiles() throws Exception {
    String[] params = { "file1", "file2" };

    fileService.doRestoreFiles( StringUtils.join( params, "," ) );

    verify( fileService.getRepoWs(), times( 1 ) ).undeleteFile( params[0], null );
    verify( fileService.getRepoWs(), times( 1 ) ).undeleteFile( params[1], null );
  }

  @Test
  public void testDoRestoreFilesException() throws Exception {
    String[] params = { "file1", "file2" };

    doThrow( new IllegalArgumentException() ).when( fileService.defaultUnifiedRepositoryWebService )
        .undeleteFile( params[0], null );

    try {
      fileService.doRestoreFiles( StringUtils.join( params, "," ) );
      fail(); //This line should never be reached
    } catch ( Exception e ) {
      verify( fileService.getRepoWs(), times( 1 ) ).undeleteFile( params[0], null );
      verify( fileService.getRepoWs(), times( 0 ) ).undeleteFile( params[1], null );
    }
  }

  @Test
  public void testDoGetFileAsInline() throws FileNotFoundException {
    /*
     * TEST 1
     */
    doReturn( true ).when( fileService ).isPath( anyString() );
    doReturn( true ).when( fileService ).isPathValid( anyString() );

    RepositoryDownloadWhitelist mockWhiteList = mock( RepositoryDownloadWhitelist.class );
    doReturn( mockWhiteList ).when( fileService ).getWhitelist();
    doReturn( true ).when( mockWhiteList ).accept( anyString() );

    RepositoryFile mockRepoFile = mock( RepositoryFile.class );
    doReturn( mockRepoFile ).when( fileService.repository ).getFile( anyString() );

    SimpleRepositoryFileData mockData = mock( SimpleRepositoryFileData.class );
    doReturn( mockData ).when( fileService.repository ).getDataForRead( any( Serializable.class ), any( Class.class ) );

    InputStream mockInputStream = mock( InputStream.class );
    doReturn( mockInputStream ).when( mockData ).getInputStream();

    StreamingOutput mockStreamingOutput = mock( StreamingOutput.class );
    doReturn( mockStreamingOutput ).when( fileService ).getStreamingOutput( mockInputStream );

    FileService.RepositoryFileToStreamWrapper wrapper = fileService.doGetFileAsInline( "test" );

    verify( fileService.repository, times( 1 ) ).getFile( anyString() );
    verify( mockWhiteList, times( 1 ) ).accept( anyString() );
    verify( fileService, times( 2 ) ).getRepository();
    verify( fileService.repository, times( 1 ) ).getDataForRead( any( Serializable.class ), any( Class.class ) );
    verify( mockData, times( 1 ) ).getInputStream();

    assertEquals( mockRepoFile, wrapper.getRepositoryFile() );
    assertEquals( mockStreamingOutput, wrapper.getOutputStream() );

    /*
     * TEST 2
     */
    doReturn( false ).when( fileService ).isPath( anyString() );
    doReturn( mockRepoFile ).when( fileService.repository ).getFileById( anyString() );

    wrapper = fileService.doGetFileAsInline( "test" );

    verify( fileService.repository, times( 1 ) ).getFileById( anyString() );
    verify( fileService, times( 4 ) ).getRepository();

    assertEquals( mockRepoFile, wrapper.getRepositoryFile() );
    assertEquals( mockStreamingOutput, wrapper.getOutputStream() );
  }

  @Test
  public void testDoGetFileAsInlineException() {

    /*
     * TEST 1
     */
    doReturn( true ).when( fileService ).isPath( anyString() );
    doReturn( false ).when( fileService ).isPathValid( anyString() );

    try {
      fileService.doGetFileAsInline( "test" );
      fail();
    } catch ( IllegalArgumentException e ) {
      // Excpected
    } catch ( FileNotFoundException e ) {
      fail();
    }

    /*
     * TEST 2
     */
    doReturn( true ).when( fileService ).isPathValid( anyString() );
    doReturn( null ).when( fileService.repository ).getFile( anyString() );

    try {
      fileService.doGetFileAsInline( "test" );
      fail();
    } catch ( FileNotFoundException e ) {
      // Expected
    }

    /*
     * TEST 3
     */
    RepositoryFile mockFile = mock( RepositoryFile.class );
    doReturn( mockFile ).when( fileService.repository ).getFile( anyString() );

    RepositoryDownloadWhitelist mockWhiteList = mock( RepositoryDownloadWhitelist.class );
    doReturn( mockWhiteList ).when( fileService ).getWhitelist();
    doReturn( false ).when( mockWhiteList ).accept( anyString() );

    IAuthorizationPolicy mockPolicy = mock( IAuthorizationPolicy.class );
    doReturn( mockPolicy ).when( fileService ).getPolicy();
    doReturn( false ).when( mockPolicy ).isAllowed( anyString() );

    try {
      fileService.doGetFileAsInline( "test" );
      fail();
    } catch ( IllegalArgumentException e ) {
      // Excpected
    } catch ( FileNotFoundException e ) {
      fail();
    }

    /*
     * TEST 4
     */
    doReturn( true ).when( mockWhiteList ).accept( anyString() );
    doThrow( new IllegalArgumentException() ).when( fileService.repository )
        .getDataForRead( any( Serializable.class ), any( Class.class ) );

    try {
      fileService.doGetFileAsInline( "test" );
      fail();
    } catch ( InternalError e ) {
      // Excpected
    } catch ( FileNotFoundException e ) {
      fail();
    }
  }
}

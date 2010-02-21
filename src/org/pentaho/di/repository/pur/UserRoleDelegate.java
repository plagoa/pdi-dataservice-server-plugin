package org.pentaho.di.repository.pur;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;

import org.apache.commons.logging.Log;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.IRole;
import org.pentaho.di.repository.RepositorySecurityManager;
import org.pentaho.di.repository.UserInfo;
import org.pentaho.platform.engine.security.userroledao.ws.IUserRoleWebService;
import org.pentaho.platform.engine.security.userroledao.ws.ProxyPentahoRole;
import org.pentaho.platform.engine.security.userroledao.ws.ProxyPentahoUser;
import org.pentaho.platform.engine.security.userroledao.ws.UserRoleException;
import org.pentaho.platform.engine.security.userroledao.ws.UserRoleSecurityInfo;

public class UserRoleDelegate {
  private UserRoleListChangeListenerCollection userRoleListChangeListeners;
  IUserRoleWebService userRoleWebService = null;
  RepositorySecurityManager rsm = null;
  Log logger;
  UserRoleLookupCache lookupCache = null;
  UserRoleSecurityInfo userRoleSecurityInfo;
  public UserRoleDelegate(RepositorySecurityManager rsm, PurRepositoryMeta repositoryMeta, UserInfo userInfo, Log logger) {
    try {
      this.logger = logger;
      final String url = repositoryMeta.getRepositoryLocation().getUrl() + "/userroleadmin?wsdl"; //$NON-NLS-1$
      Service service = Service.create(new URL(url), new QName("http://www.pentaho.org/ws/1.0", //$NON-NLS-1$
          "UserRoleWebServiceService"));//$NON-NLS-1$
      userRoleWebService = service.getPort(IUserRoleWebService.class);
      ((BindingProvider) userRoleWebService).getRequestContext().put(BindingProvider.USERNAME_PROPERTY,
          userInfo.getLogin());
      ((BindingProvider) userRoleWebService).getRequestContext().put(BindingProvider.PASSWORD_PROPERTY,
          userInfo.getPassword());
      this.rsm = rsm;
      updateUserRoleInfo();
      initializeLookupCache();
    } catch (Exception e) {
      this.logger.error(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0001_UNABLE_TO_INITIALIZE_USER_ROLE_WEBSVC"), e); //$NON-NLS-1$
    }
  }

  public void initializeLookupCache() {
    lookupCache = new UserRoleLookupCache(userRoleWebService, rsm);
  }
  public void updateUserRoleInfo() throws UserRoleException{
      userRoleSecurityInfo = userRoleWebService.getUserRoleSecurityInfo();
  }
  public void createUser(UserInfo newUser) throws KettleException {
    try {
      ProxyPentahoUser user = UserRoleHelper.convertToPentahoProxyUser(newUser);
      userRoleWebService.createUser(user);
      userRoleWebService.setRoles(user, UserRoleHelper.convertToPentahoProxyRoles(newUser.getRoles()));
      lookupCache.insertUserToLookupSet(newUser);
      fireUserRoleListChange();
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0002_UNABLE_TO_CREATE_USER"), e); //$NON-NLS-1$
    }
  }

  public void deleteUsers(List<UserInfo> users) throws KettleException {
    try {
      userRoleWebService.deleteUsers(UserRoleHelper.convertToPentahoProxyUsers(users));
      lookupCache.removeUsersFromLookupSet(users);
      fireUserRoleListChange();
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0003_UNABLE_TO_DELETE_USERS"), e); //$NON-NLS-1$
    }
  }

  public void deleteUser(String name) throws KettleException {
    try {
      ProxyPentahoUser user = userRoleWebService.getUser(name);
      if (user != null) {
        ProxyPentahoUser[] users = new ProxyPentahoUser[1];
        users[0] = user;
        userRoleWebService.deleteUsers(users);
        fireUserRoleListChange();        
      } else {
        throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0004_UNABLE_TO_DELETE_USER", name)); //$NON-NLS-1$       
      }
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0004_UNABLE_TO_DELETE_USER", name), e); //$NON-NLS-1$
    }
  }

  public void setUsers(List<UserInfo> users) throws KettleException {
    // TODO Figure out what to do here
  }

  public UserInfo getUser(String name, String password) throws KettleException {
    UserInfo userInfo = null;
    try {
      ProxyPentahoUser user = userRoleWebService.getUser(name);
      if (user != null && user.getName().equals(name) && user.getPassword().equals(password)) {
        userInfo = UserRoleHelper.convertToUserInfo(user, userRoleWebService.getRolesForUser(user), rsm);
      }
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0005_UNABLE_TO_GET_USER", name), e); //$NON-NLS-1$
    }
    return userInfo;
  }

  public UserInfo getUser(String name) throws KettleException {
    UserInfo userInfo = null;
    try {
      ProxyPentahoUser user = userRoleWebService.getUser(name);
      if (user != null && user.getName().equals(name)) {
        userInfo = UserRoleHelper.convertToUserInfo(user, userRoleWebService.getRolesForUser(user), rsm);
      }
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0005_UNABLE_TO_GET_USER", name), e); //$NON-NLS-1$
    }
    return userInfo;
  }

  public List<UserInfo> getUsers() throws KettleException {
    try {
      return UserRoleHelper.convertFromProxyPentahoUsers(userRoleSecurityInfo, rsm);
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0006_UNABLE_TO_GET_USERS"), e); //$NON-NLS-1$
    }
  }

  public void updateUser(UserInfo user) throws KettleException {
    try {
      ProxyPentahoUser proxyUser = UserRoleHelper.convertToPentahoProxyUser(user);
      userRoleWebService.updateUser(proxyUser);
      userRoleWebService.setRoles(proxyUser, UserRoleHelper.convertToPentahoProxyRoles(user.getRoles()));
      lookupCache.updateUserInLookupSet(user);
      fireUserRoleListChange();      
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0007_UNABLE_TO_UPDATE_USER", user.getLogin()), e); //$NON-NLS-1$
    }
  }

  public void createRole(IRole newRole) throws KettleException {
    try {
      ProxyPentahoRole role = UserRoleHelper.convertToPentahoProxyRole(newRole);
      userRoleWebService.createRole(role);
      userRoleWebService.setUsers(role, UserRoleHelper.convertToPentahoProxyUsers(newRole.getUsers()));
      lookupCache.insertRoleToLookupSet(newRole);
      fireUserRoleListChange();
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0008_UNABLE_TO_CREATE_ROLE", newRole.getName()), e); //$NON-NLS-1$
    }
  }

  public void deleteRoles(List<IRole> roles) throws KettleException {
    try {
      userRoleWebService.deleteRoles(UserRoleHelper.convertToPentahoProxyRoles(roles));
      lookupCache.removeRolesFromLookupSet(roles);
      fireUserRoleListChange();
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0009_UNABLE_TO_DELETE_ROLES"), e); //$NON-NLS-1$
    }

  }

  public IRole getRole(String name) throws KettleException {
    try {
      return UserRoleHelper.convertFromProxyPentahoRole(userRoleWebService, UserRoleHelper.getProxyPentahoRole(
          userRoleWebService, name), lookupCache, rsm);
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0010_UNABLE_TO_GET_ROLE", name), e); //$NON-NLS-1$
    }
  }
  
  public List<IRole> getRoles() throws KettleException {
    try {
      return UserRoleHelper.convertToListFromProxyPentahoRoles(userRoleSecurityInfo, rsm);
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0011_UNABLE_TO_GET_ROLES"), e); //$NON-NLS-1$
    }
  }

  public void updateRole(IRole role) throws KettleException {
    try {
      List<String> users = new ArrayList<String>();
      for (UserInfo user : role.getUsers()) {
        users.add(user.getLogin());
      }
      userRoleWebService.updateRole(role.getName(), role.getDescription(), users);
      lookupCache.updateRoleInLookupSet(role);
      fireUserRoleListChange();
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0012_UNABLE_TO_UPDATE_ROLE", role.getName()), e); //$NON-NLS-1$
    }

  }

  public void deleteRole(String name) throws KettleException {
    try {
      ProxyPentahoRole roleToDelete = UserRoleHelper.getProxyPentahoRole(userRoleWebService, name);
      if (roleToDelete != null) {
        ProxyPentahoRole[] roleArray = new ProxyPentahoRole[1];
        roleArray[0] = roleToDelete;
        userRoleWebService.deleteRoles(roleArray);
        fireUserRoleListChange();
      } else {
        throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0013_UNABLE_TO_DELETE_ROLE", name)); //$NON-NLS-1$
      }
    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(UserRoleDelegate.class, "UserRoleDelegate.ERROR_0013_UNABLE_TO_DELETE_ROLE", name), e); //$NON-NLS-1$
    }
  }

  public void setRoles(List<IRole> roles) throws KettleException {
    // TODO Figure out what to do here
  }
 
  public void addUserRoleListChangeListener(IUserRoleListChangeListener listener) {
    if (userRoleListChangeListeners == null) {
      userRoleListChangeListeners = new UserRoleListChangeListenerCollection();
    }
    userRoleListChangeListeners.add(listener);
  }

  public void removeUserRoleListChangeListener(IUserRoleListChangeListener listener) {
    if (userRoleListChangeListeners != null) {
      userRoleListChangeListeners.remove(listener);
    }
  }

  /**
   * Fire all current {@link IUserRoleListChangeListener}.
   */
  void fireUserRoleListChange() {

    if (userRoleListChangeListeners != null) {
      userRoleListChangeListeners.fireOnChange();
    }
  }
}
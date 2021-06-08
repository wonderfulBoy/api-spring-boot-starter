package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.PackageDoc;
import com.sun.tools.doclets.internal.toolkit.Configuration;

import java.util.*;

public class Group {

    private final Configuration configuration;
    private Map<String, String> regExpGroupMap = new HashMap<String, String>();
    private List<String> sortedRegExpList = new ArrayList<String>();
    private List<String> groupList = new ArrayList<String>();
    private Map<String, String> pkgNameGroupMap = new HashMap<String, String>();

    public Group(Configuration configuration) {
        this.configuration = configuration;
    }

    public boolean checkPackageGroups(String groupname,
                                      String pkgNameFormList) {
        StringTokenizer strtok = new StringTokenizer(pkgNameFormList, ":");
        if (groupList.contains(groupname)) {
            configuration.message.warning("doclet.Groupname_already_used", groupname);
            return false;
        }
        groupList.add(groupname);
        while (strtok.hasMoreTokens()) {
            String id = strtok.nextToken();
            if (id.length() == 0) {
                configuration.message.warning("doclet.Error_in_packagelist", groupname, pkgNameFormList);
                return false;
            }
            if (id.endsWith("*")) {
                id = id.substring(0, id.length() - 1);
                if (foundGroupFormat(regExpGroupMap, id)) {
                    return false;
                }
                regExpGroupMap.put(id, groupname);
                sortedRegExpList.add(id);
            } else {
                if (foundGroupFormat(pkgNameGroupMap, id)) {
                    return false;
                }
                pkgNameGroupMap.put(id, groupname);
            }
        }
        Collections.sort(sortedRegExpList, new MapKeyComparator());
        return true;
    }

    boolean foundGroupFormat(Map<String, ?> map, String pkgFormat) {
        if (map.containsKey(pkgFormat)) {
            configuration.message.error("doclet.Same_package_name_used", pkgFormat);
            return true;
        }
        return false;
    }

    public Map<String, List<PackageDoc>> groupPackages(PackageDoc[] packages) {
        Map<String, List<PackageDoc>> groupPackageMap = new HashMap<String, List<PackageDoc>>();
        String defaultGroupName =
                (pkgNameGroupMap.isEmpty() && regExpGroupMap.isEmpty()) ?
                        configuration.message.getText("doclet.Packages") :
                        configuration.message.getText("doclet.Other_Packages");

        if (!groupList.contains(defaultGroupName)) {
            groupList.add(defaultGroupName);
        }
        for (int i = 0; i < packages.length; i++) {
            PackageDoc pkg = packages[i];
            String pkgName = pkg.name();
            String groupName = pkgNameGroupMap.get(pkgName);


            if (groupName == null) {
                groupName = regExpGroupName(pkgName);
            }


            if (groupName == null) {
                groupName = defaultGroupName;
            }
            getPkgList(groupPackageMap, groupName).add(pkg);
        }
        return groupPackageMap;
    }

    String regExpGroupName(String pkgName) {
        for (int j = 0; j < sortedRegExpList.size(); j++) {
            String regexp = sortedRegExpList.get(j);
            if (pkgName.startsWith(regexp)) {
                return regExpGroupMap.get(regexp);
            }
        }
        return null;
    }

    List<PackageDoc> getPkgList(Map<String, List<PackageDoc>> map, String groupname) {
        List<PackageDoc> list = map.get(groupname);
        if (list == null) {
            list = new ArrayList<PackageDoc>();
            map.put(groupname, list);
        }
        return list;
    }

    public List<String> getGroupList() {
        return groupList;
    }

    private static class MapKeyComparator implements Comparator<String> {
        public int compare(String key1, String key2) {
            return key2.length() - key1.length();
        }
    }
}

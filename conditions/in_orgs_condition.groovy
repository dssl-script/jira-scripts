import com.atlassian.jira.user.ApplicationUser
import com.opensymphony.workflow.WorkflowContext;
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.servicedesk.api.ServiceDeskManager
import com.atlassian.servicedesk.api.organization.OrganizationService
import com.atlassian.servicedesk.api.util.paging.SimplePagedRequest
import com.onresolve.scriptrunner.runner.customisers.PluginModule
import com.onresolve.scriptrunner.runner.customisers.WithPlugin

import org.apache.log4j.Logger
import org.apache.log4j.Level


class InOrgConditionCheck {

    Logger log = Logger.getLogger("com.acme.CreateSubtask");


    def InOrgConditionCheck() {
        log.setLevel(Level.DEBUG);
    }

    def userInOrg(user, proj_key) {
        def ORGS_LIST = [
                //"РЖД",
                "Пятерочка",
        ]

        @WithPlugin("com.atlassian.servicedesk")

        @PluginModule
        ServiceDeskManager serviceDeskManager

        @PluginModule
        OrganizationService organizationService

        def projectObject = ComponentAccessor.getProjectManager().getProjectByCurrentKey(proj_key)
        def serviceDeskProject = serviceDeskManager.getServiceDeskForProject(projectObject)

        if (serviceDeskProject.isLeft()) {
            return false
        }

        def serviceDeskId = serviceDeskProject?.right()?.get()?.id as Integer
        def organizationsQuery = organizationService.newOrganizationsQueryBuilder().serviceDeskId(serviceDeskId).build()
        def access_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")

        def result = organizationService.getOrganizations(access_user, organizationsQuery)

        if (result.isLeft()) {
            return false
        }
        def orgs_list = []
        try {
            orgs_list = result.right().get().getResults()
        } catch (Exception e){
            // Pass
        }
        for (x in orgs_list) {
            if (ORGS_LIST.contains(x.getName())) {
                def usersInOrganizationQuery = organizationService
                        .newUsersInOrganizationQuery()
                        .customerOrganization(x)
                        .pagedRequest(new SimplePagedRequest(0, 50))
                        .build()
                def usersInOrgResult = organizationService.getUsersInOrganization(access_user, usersInOrganizationQuery)
                for (org_user in usersInOrgResult.right().get().getResults()) {
                    if (org_user == user) {
                        return true
                    }
                }
            }
        }
        return false
    }

    def groupsEmptyAndInOrg(user) {
        def groups_assigned = ComponentAccessor.getGroupManager().getGroupsForUser(user)
        def in_org = userInOrg(user, "VP")
        if (!groups_assigned.size() && in_org) {
            return true
        } else {
            return false
        }
    }

    def run() {
        //def watching_user = ComponentAccessor.getUserManager().getUserByName("pashok777@mail.ru")
        def watching_user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
        return groupsEmptyAndInOrg(watching_user)
    }

    def run_test() {
        def test_user = ""
        def res = false

        def test_users = [
                ["name": "pashok777@mail.ru", "res": false],
                ["name": "v.monakhov", "res": false],
                ["name": "0037@okskoe.com", "res": false],
                ["name": "Konstantin", "res": true],
                ["name": "d.serbin", "res": false],
                ["name": "a.potapov", "res": false],
                ["name": "baatr", "res": true],
                ["name": "a.proschalyigin", "res": false],
                ["name": "s-vasiliev", "res": false],
                ["name": "i-sherbakov", "res": false],
                ["name": "Evgeny.Polovinka@x5.ru", "res": true],
                ["name": "amatveeva@x5.ru", "res": true],
                ["name": "Igor Elchaninov", "res": true],
                ["name": "Oleg.Pavlenko@x5.ru", "res": true],
                ["name": "o.proskuryakov@x5.ru", "res": true],
                ["name": "sergey.bezvidnyy", "res": true],
                ["name": "Pavel.Kruglov@x5.ru", "res": true],
                ["name": "s-savin", "res": false],
        ]
        def text = ""
        for (x in test_users) {
            test_user = ComponentAccessor.getUserManager().getUserByName(x["name"])
            res = groupsEmptyAndInOrg(test_user)
            text += "\nПользователь: " + test_user + "; Видит: " + res + "\n"
            //log.debug("\nuser: " + test_user + "\nexpected: " + x["res"] + "\nresult: " + res)
            assert res == x["res"]
        }
        log.debug(text)
    }


}

InOrgConditionCheck script = new InOrgConditionCheck();
//passesCondition = script.run()
script.run_test()



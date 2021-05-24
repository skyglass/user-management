# User Management Microservice

REST API for user management microservice with swagger UI is available on 
# https://users.skycomposer.net/usermgmt/swagger-ui/index.html

# Microservices Deployment on AWS with Terraform and K3S:

## Step 01 - Setup terraform on AWS

Setting Up an AWS Operations Account

Log in to your AWS management console with your root user credentials. Once you’ve logged in, you should be presented with a list of AWS services. Find and select the IAM service.

Select the Users link from the IAM navigation menu on the lefthand side of the screen. Click the Add user button to start the IAM user creation process, as shown in Figure 6-3.

Enter ops-account in the User name field. We also want to use this account to acccess the CLI and API, so select “Programmatic access” as the AWS “Access type,”

Our operator account will need a lot of permissions to do work in AWS on our behalf. For now, however, we’re only going to attach a single set of permissions, packaged together in an AWS policy called IAMFullAccess.

To add this policy, select “Attach existing policies directly” from the set of options at the top. Search for a policy called IAMFullAccess and select it by ticking its checkbox

If everything looks OK to you, click the Create user button.

Access key and secret key

Before we do anything else, we’ll need to make a note of our new user’s keys. Click the Show link and copy and paste both the “Access key ID” and the “Secret access key” into a temporary file. We’ll use both of these later in this section with our automated pipeline. Be careful with this key material as it will give whoever has it an opportunity to create resources in your AWS environment—at your expense.

Make sure you take note of the access key ID and the secret access key that were generated before you leave this screen. You’ll need them later.

We have now created a user called ops-account with permission to make IAM changes. That gives us all that we need to transition from using the browser-based console over to the AWS CLI application that we installed earlier. The first thing we’ll need to do is configure the CLI to use the ops user we’ve just created.

Configure the AWS CLI

$ aws configure
AWS Access Key ID [****************AMCK]: AMIB3IIUDHKPENIBWUVGR
AWS Secret Access Key [****************t+ND]: /xd5QWmsqRsM1Lj4ISUmKoqV7/...
Default region name [None]: eu-west-2
Default output format [None]: json

You can test that you’ve configured the CLI correctly by listing the user accounts that have been created. Run the iam list-users command to test your setup:

$ aws iam list-users
{
    "Users": [
        {
            "Path": "/",
            "UserName": "admin",
            "UserId": "AYURIGDYE7PXW3QCYYEWM",
            "Arn": "arn:aws:iam::842218941332:user/admin",
            "CreateDate": "2019-03-21T14:01:03+00:00"
        },
        {
            "Path": "/",
            "UserName": "ops-account",
            "UserId": "AYUR4IGBHKZTE3YVBO2OB",
            "Arn": "arn:aws:iam::842218941332:user/ops-account",
            "CreateDate": "2020-07-06T15:15:31+00:00"
        }
    ]
}

If you’ve done everything correctly, you should see a list of your AWS user accounts. That indicates that AWS CLI is working properly and has access to your instance. Now, we can set up the permissions our operations account will need.

Setting Up AWS Permissions

When we created our ops-account user we attached an IAM policy to it that only gives it permission to modify IAM settings. But our ops account will need a lot more permissions than that to manage the AWS resources we’ll need for our infrastructure build. In this section, we’ll use the AWS command-line tool to create and attach additional permission policies to the ops account.

The first thing we’ll do is make the ops-account user part of a new group called Ops-Accounts. That way we’ll be able to assign new users to the group if we want them to have the same permissions. Use the following command to create a new group called Ops-Accounts:

$ aws iam create-group --group-name Ops-Accounts

If this is successful, the AWS CLI will display the group that has been created:

{

    "Group": {
        "Path": "/",
        "GroupName": "Ops-Accounts",
        "GroupId": "AGPA4IGBHKZTGWGQWW67X",
        "Arn": "arn:aws:iam::842218941332:group/Ops-Accounts",
        "CreateDate": "2020-07-06T15:29:14+00:00"
    }
}

Now, we just need to add our user to the new group. Use the following command to do that:

$ aws iam add-user-to-group --user-name ops-account --group-name Ops-Accounts

If it works, you’ll get no response from the CLI.

Next, we need to attach a set of permissions to our Ops-Account group. Those permissions will automatically be applied to our operations users, since we’ve made it part of the group. The permissions we’ll be attaching will let our user create and change AWS resources. 

Run the following command to attach all the policies we’ll need to the Ops-Accounts group:

$ aws iam attach-group-policy --group-name Ops-Accounts\
 --policy-arn arn:aws:iam::aws:policy/IAMFullAccess &&\
aws iam attach-group-policy --group-name Ops-Accounts\
 --policy-arn arn:aws:iam::aws:policy/AmazonEC2FullAccess &&\
aws iam attach-group-policy --group-name Ops-Accounts\
 --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryFullAccess &&\
aws iam attach-group-policy --group-name Ops-Accounts\
 --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy &&\
aws iam attach-group-policy --group-name Ops-Accounts\
 --policy-arn arn:aws:iam::aws:policy/AmazonEKSServicePolicy &&\
aws iam attach-group-policy --group-name Ops-Accounts\
 --policy-arn arn:aws:iam::aws:policy/AmazonVPCFullAccess &&\
aws iam attach-group-policy --group-name Ops-Accounts\
 --policy-arn arn:aws:iam::aws:policy/AmazonRoute53FullAccess &&\
aws iam attach-group-policy --group-name Ops-Accounts\
 --policy-arn arn:aws:iam::aws:policy/AmazonS3FullAccess



In AWS, every resource has a unique identifier called an Amazon Resource Name (ARN). The string of digits in the ARN of the policy you’ve just created will be unique to you and your AWS instance. You’ll need to make note of your policy’s ARN string so that you can reference it in the following steps.

With the new policy created, all that’s left is to attach it to our user group. Run the following command, replacing the token we’ve called {YOUR_POLICY_ARN} with the ARN from your policy:

$ aws iam attach-group-policy --group-name Ops-Accounts \
   --policy-arn {YOUR_POLICY_ARN}

You now have an ops-account user that has the permissions needed to automatically create AWS infrastructure resources for us. We’ll be using this user account when we write our Terraform code and when we configure the infrastructure pipeline. Make sure you keep the access key and secret somewhere handy (and safe) as we’ll need it later.

We have one last bit of setup to take care of before we can get to work building the pipeline: the creation of an AWS S3 storage bucket for Terraform to store state.

Creating an S3 Backend for Terraform

To create a bucket, you’ll need to give it a unique name and pick the region that it should reside in. You should have already selected a default region when you configured the AWS CLI and we suggest that you use the same region for the S3 bucket. You can find more information about S3 bucket regions in the AWS documentation.

If you are hosting your bucket in the us-east-1 region, use the following command:

$ aws s3api create-bucket --bucket {YOUR_S3_BUCKET_NAME} --region us-east-1

If you are hosting the s3 bucket in a region other than us-east-1, use the following command:


If everything has gone well, you should see a JSON object with the location of your bucket. It will look something like this example for a bucket named my-msur-test:

{
    "Location": "http://my-msur-test.s3.amazonaws.com/"
}


## Step-01: What are we going to learn in this section?
- We are going to deploy two microservices.
    - User Management Service
    - Notification Service

### Usecase Description
- User Management **Create User API**  will call Notification service **Send Notification API** to send an email to user when we create a user. 


### List of Docker Images used in this section
| Application Name                 | Docker Image Name                          |
| ------------------------------- | --------------------------------------------- |
| User Management Microservice | stacksimplify/kube-usermanagement-microservice:1.0.0 |
| Notifications Microservice V1 | stacksimplify/kube-notifications-microservice:1.0.0 |
| Notifications Microservice V2 | stacksimplify/kube-notifications-microservice:2.0.0 |

## Step-02: Pre-requisite -1: AWS RDS Database, ALB Ingress Controller & External DNS

### AWS RDS Database
- We have created AWS RDS Database as part of section [06-EKS-Storage-with-RDS-Database](/06-EKS-Storage-with-RDS-Database/README.md)
- We even created a `externalName service: 01-MySQL-externalName-Service.yml` in our Kubernetes manifests to point to that RDS Database. 

### ALB Ingress Controller & External DNS
- We are going to deploy a application which will also have a `ALB Ingress Service` and also will register its DNS name in Route53 using `External DNS`
- Which means we should have both related pods running in our EKS cluster. 
- We have installed **ALB Ingress Controller** as part of section [08-01-ALB-Ingress-Install](/08-ELB-Application-LoadBalancers/08-01-ALB-Ingress-Install/README.md)
- We have installed **External DNS** as part of section [08-06-01-Deploy-ExternalDNS-on-EKS](/08-ELB-Application-LoadBalancers/08-06-ALB-Ingress-ExternalDNS/08-06-01-Deploy-ExternalDNS-on-EKS/README.md)
```
# Verify alb-ingress-controller pod running in namespace kube-system
kubectl get pods -n kube-system

# Verify external-dns pod running in default namespace
kubectl get pods
```


## Step-03: Pre-requisite-2: Create Simple Email Service - SES SMTP Credentials
### SMTP Credentials
- Go to Services -> Simple Email Service
- SMTP Settings --> Create My SMTP Credentials
- **IAM User Name:** append the default generated name with microservice or something so we have a reference of this IAM user created for our ECS Microservice deployment
- Download the credentials and update the same for below environment variables which you are going to provide in kubernetes manifest `04-NotificationMicroservice-Deployment.yml`
```
AWS_MAIL_SERVER_HOST=email-smtp.us-east-1.amazonaws.com
AWS_MAIL_SERVER_USERNAME=****
AWS_MAIL_SERVER_PASSWORD=***
AWS_MAIL_SERVER_FROM_ADDRESS= use-a-valid-email@gmail.com 
```
- **Important Note:** Environment variable AWS_MAIL_SERVER_FROM_ADDRESS value should be a **valid** email address and also verified in SES. 

### Verfiy Email Addresses to which notifications we need to send.
- We need two email addresses for testing Notification Service.  
-  **Email Addresses**
    - Verify a New Email Address
    - Email Address Verification Request will be sent to that address, click on link to verify your email. 
    - **From Address:** stacksimplify@gmail.com (replace with your ids during verification)
    - **To Address:** dkalyanreddy@gmail.com (replace with your ids during verification)
- **Important Note:** We need to ensure all the emails (FromAddress email) and (ToAddress emails) to be verified here. 
    - Reference Link: https://docs.aws.amazon.com/ses/latest/DeveloperGuide/verify-email-addresses.html    
- Environment Variables
    - AWS_MAIL_SERVER_HOST=email-smtp.us-east-1.amazonaws.com
    - AWS_MAIL_SERVER_USERNAME=*****
    - AWS_MAIL_SERVER_PASSWORD=*****
    - AWS_MAIL_SERVER_FROM_ADDRESS=stacksimplify@gmail.com


## Step-04: Create Notification Microservice Deployment Manifest
- Update environment Variables for Notification Microservice
- **Notification Microservice Deployment**
```yml
          - name: AWS_MAIL_SERVER_HOST
            value: "smtp-service"
          - name: AWS_MAIL_SERVER_USERNAME
            value: "AKIABCDEDFASUBKLDOAX"
          - name: AWS_MAIL_SERVER_PASSWORD
            value: "Bdsdsadsd32qcsads65B4oLo7kMgmKZqhJtEipuE5unLx"
          - name: AWS_MAIL_SERVER_FROM_ADDRESS
            value: "stacksimplify@gmail.com"
```

## Step-05: Create Notification Microservice SMTP ExternalName Service
```yml
apiVersion: v1
kind: Service
metadata:
  name: smtp-service
spec:
  type: ExternalName
  externalName: email-smtp.us-east-1.amazonaws.com
```

## Step-06: Create Notification Microservice NodePort Service
```yml
apiVersion: v1
kind: Service
metadata:
  name: notification-clusterip-service
  labels:
    app: notification-restapp
spec:
  type: ClusterIP
  selector:
    app: notification-restapp
  ports:
  - port: 8096
    targetPort: 8096
```
## Step-07: Update User Management Microservice Deployment Manifest with Notification Service Environment Variables. 
- User Management Service new environment varibales related to Notification Microservice in addition to already which were configured related to MySQL
- Update in `02-UserManagementMicroservice-Deployment.yml`
```yml
          - name: NOTIFICATION_SERVICE_HOST
            value: "notification-clusterip-service"
          - name: NOTIFICATION_SERVICE_PORT
            value: "8096"    
```
## Step-08: Update ALB Ingress Service Kubernetes Manifest
- Update Ingress Service to ensure only target it is going to have is User Management Service
- Remove /app1, /app2 contexts
```yml
    # External DNS - For creating a Record Set in Route53
    external-dns.alpha.kubernetes.io/hostname: services.kubeoncloud.com, ums.kubeoncloud.com
spec:
  rules:
    - http:
        paths:
          - path: /* # SSL Redirect Setting
            backend:
              serviceName: ssl-redirect
              servicePort: use-annotation                   
          - path: /*
            backend:
              serviceName: usermgmt-restapp-nodeport-service
              servicePort: 8095              
```

## Step-09: Deploy Microservices manifests
```
# Deploy Microservices manifests
kubectl apply -f V1-Microservices/
```

## Step-10: Verify the Deployment using kubectl
```
# List Pods
kubectl get pods

# User Management Microservice Logs
kubectl logs -f $(kubectl get po | egrep -o 'usermgmt-microservice-[A-Za-z0-9-]+')

# Notification Microservice Logs
kubectl logs -f $(kubectl get po | egrep -o 'notification-microservice-[A-Za-z0-9-]+')

# External DNS Logs
kubectl logs -f $(kubectl get po | egrep -o 'external-dns-[A-Za-z0-9-]+')

# List Ingress
kubectl get ingress
```

## Step-11: Verify Microservices health-status via browser
```
# User Management Service Health-Status
https://services.kubeoncloud.com/usermgmt/health-status

# Notification Microservice Health-Status via User Management
https://services.kubeoncloud.com/usermgmt/notification-health-status
https://services.kubeoncloud.com/usermgmt/notification-service-info
```

## Step-12: Import postman project to Postman client on our desktop. 
- Import postman project
- Add environment url 
    - https://services.kubeoncloud.com (**Replace with your ALB DNS registered url on your environment**)

## Step-13: Test both Microservices using Postman
### User Management Service
- **Create User**
    - Verify the email id to confirm account creation email received.
- **List User**   
    - Verify if newly created user got listed. 
    


## Step-14: Rollout New Deployment - Set Image Option
```
# Rollout New Deployment using Set Image
kubectl set image deployment/notification-microservice notification-service=stacksimplify/kube-notifications-microservice:2.0.0 --record=true

# Verify Rollout Status
kubectl rollout status deployment/notification-microservice

# Verify ReplicaSets
kubectl get rs

# Verify Rollout History
kubectl rollout history deployment/notification-microservice

# Access Application (Should see V2)
https://services.kubeoncloud.com/usermgmt/notification-health-status

# Roll back to Previous Version
kubectl rollout undo deployment/notification-microservice

# Access Application (Should see V1)
https://services.kubeoncloud.com/usermgmt/notification-health-status
```    

## Step-15: Rollout New Deployment - kubectl Edit
```
# Rollout New Deployment using kubectl edit, change image version to 2.0.0
kubectl edit deployment/notification-microservice

# Verify Rollout Status
kubectl rollout status deployment/notification-microservice

# Verify ReplicaSets
kubectl get rs

# Verify Rollout History
kubectl rollout history deployment/notification-microservice

# Access Application (Should see V2)
https://services.kubeoncloud.com/usermgmt/notification-health-status

# Roll back to Previous Version
kubectl rollout undo deployment/notification-microservice

# Access Application (Should see V1)
https://services.kubeoncloud.com/usermgmt/notification-health-status
```

## Step-16: Rollout New Deployment - Update manifest & kubectl apply
```
# Rollout New Deployment by updating yaml manifest 2.0.0
kubectl apply -f kube-manifests/

# Verify Rollout Status
kubectl rollout status deployment/notification-microservice

# Verify ReplicaSets
kubectl get rs

# Verify Rollout History
kubectl rollout history deployment/notification-microservice

# Access Application (Should see V2)
https://services.kubeoncloud.com/usermgmt/notification-health-status

# Roll back to Previous Version
kubectl rollout undo deployment/notification-microservice

# Access Application (Should see V1)
https://services.kubeoncloud.com/usermgmt/notification-health-status
```

## Step-17: Clean-up
```
kubectl delete -f kube-manifests/    
```

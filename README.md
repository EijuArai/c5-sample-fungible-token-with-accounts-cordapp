# Sample FungibleToken Cordapp With Accounts

This Sample FungibleToken Cordapp is implementing TokenSelectionAPI provided in Corda5 and custom Accounts mimicking C4's Accounts.
This Cordapp includes Account creation Flow, Token issuance Flow, Token transfer Flow, and Token balance query Flow.

### How to use

In Corda 5, flows are triggered from `POST /flow/{holdingidentityshorthash}`, and the flow status can be checked at `GET /flow/{holdingidentityshorthash}/{clientrequestid}`.
* holdingidentityshorthash: This is the ID of network participants. The IDs of all participants in the network can be checked with the `ListVNodes` task.
* clientrequestid: This is the ID given to the process of the flow to be executed.

#### Step 1: Let's Create Accounts
We'll create issuer, owner, and new-owner accounts.
Go to `POST /flow/{holdingidentityshorthash}` with Alice's Vnode ID and POST with the following Request Body.
Create an account and share it with Bob.
```
{
  "clientRequestId": "create-1",
  "flowClassName": "com.r3.accounts.workflows.CreateAccount",
  "requestBody": {
    "metadata": "issuer",
    "shareParty": "CN=Bob,OU=Test Dept,O=R3,L=London,C=GB"
  }
}
```

```
{
  "clientRequestId": "create-2",
  "flowClassName": "com.r3.accounts.workflows.CreateAccount",
  "requestBody": {
    "metadata": "owner",
    "shareParty": "CN=Bob,OU=Test Dept,O=R3,L=London,C=GB"
  }
}
```

```
{
  "clientRequestId": "create-3",
  "flowClassName": "com.r3.accounts.workflows.CreateAccount",
  "requestBody": {
    "metadata": "new_owner",
    "shareParty": "CN=Bob,OU=Test Dept,O=R3,L=London,C=GB"
  }
}
```

Check the flow status at `GET /flow/{holdingidentityshorthash}/{clientrequestid}`.

#### Step 2: Let's Issue Tokens
We'll issue 100 units of USD to the owner.
Go to `POST /flow/{holdingidentityshorthash}` and POST with the following Request Body.
For ownerId and issuerId, you should have received UUIDs for each account in the response when creating accounts in Step 1, so please input those.
```
{
  "clientRequestId": "issue-1",
  "flowClassName": "com.r3.token.fungible.workflows.IssueTokenFlow",
  "requestBody": {
    "ownerId": ":ownerId",
    "issuerId": ":issuerId",
    "symbol": "USD",
    "amount": 100
  }
}
```

As usual, check the flow status at `GET /flow/{holdingidentityshorthash}/{clientrequestid}`.

#### Step 3: Let's Transfer Tokens
We'll transfer 50 units of USD issued to owner in Step 2 to new_owner.
Go to `POST /flow/{holdingidentityshorthash}` and POST with the following Request Body.
For ownerId, issuerId, and newOwnerId...
```
{
  "clientRequestId": "transfer-1",
  "flowClassName": "com.r3.token.fungible.workflows.TransferTokenFlow",
  "requestBody": {
    "issuerId": ":issuerId",
    "newOwnerId": ":newOwnerId",
    "ownerId": ":ownerId",
    "symbol": "USD",
    "amount": 50
  }
}
```
As usual, check the flow status at `GET /flow/{holdingidentityshorthash}/{clientrequestid}`.

#### Step 4: Let's Redeem Tokens
We'll redeem 50 units of USD issued to owner in Step 2.
Go to `POST /flow/{holdingidentityshorthash}` and POST with the following Request Body.
For ownerId and issuerId...
```
{
  "clientRequestId": "redeem-1",
  "flowClassName": "com.r3.token.fungible.workflows.RedeemTokenFlow",
  "requestBody": {
    "issuerId": ":issuerId",
    "ownerId": ":ownerId",
    "symbol": "USD",
    "amount": 50
  }
}
```
As usual, check the flow status at `GET /flow/{holdingidentityshorthash}/{clientrequestid}`.

#### Step 5: Let's Check the Balance
We'll query the owner's Token balance.
Go to `POST /flow/{holdingidentityshorthash}` and POST with the following Request Body.
For ownerId and issuerId...
```
{
  "clientRequestId": "get-1",
  "flowClassName": "com.r3.token.fungible.workflows.GetTokenBalanceFlow",
  "requestBody": {
    "issuerId": ":issuerId",
    "ownerId": ":ownerId",
    "symbol": "USD"
  }
}
```
As usual, check the flow status at `GET /flow/{holdingidentityshorthash}/{clientrequestid}`.

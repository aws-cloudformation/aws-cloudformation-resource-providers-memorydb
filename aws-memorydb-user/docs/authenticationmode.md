# AWS::MemoryDB::User AuthenticationMode

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "<a href="#type" title="Type">Type</a>" : <i>String</i>,
    "<a href="#passwords" title="Passwords">Passwords</a>" : <i>[ String, ... ]</i>
}
</pre>

### YAML

<pre>
<a href="#type" title="Type">Type</a>: <i>String</i>
<a href="#passwords" title="Passwords">Passwords</a>: <i>
      - String</i>
</pre>

## Properties

#### Type

Type of authentication strategy for this user.

_Required_: No

_Type_: String

_Allowed Values_: <code>password</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Passwords

Passwords used for this user account. You can create up to two passwords for each user.

_Required_: No

_Type_: List of String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

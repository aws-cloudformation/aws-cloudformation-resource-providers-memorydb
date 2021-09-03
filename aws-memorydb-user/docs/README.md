# AWS::MemoryDB::User

Resource Type definition for AWS::MemoryDB::User

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::MemoryDB::User",
    "Properties" : {
        "<a href="#username" title="UserName">UserName</a>" : <i>String</i>,
        "<a href="#accessstring" title="AccessString">AccessString</a>" : <i>String</i>,
        "<a href="#authenticationmode" title="AuthenticationMode">AuthenticationMode</a>" : <i><a href="authenticationmode.md">AuthenticationMode</a></i>,
        "<a href="#tags" title="Tags">Tags</a>" : <i>[ <a href="tag.md">Tag</a>, ... ]</i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::MemoryDB::User
Properties:
    <a href="#username" title="UserName">UserName</a>: <i>String</i>
    <a href="#accessstring" title="AccessString">AccessString</a>: <i>String</i>
    <a href="#authenticationmode" title="AuthenticationMode">AuthenticationMode</a>: <i><a href="authenticationmode.md">AuthenticationMode</a></i>
    <a href="#tags" title="Tags">Tags</a>: <i>
      - <a href="tag.md">Tag</a></i>
</pre>

## Properties

#### UserName

The name of the user.

_Required_: Yes

_Type_: String

_Pattern_: <code>[a-z][a-z0-9\\-]*</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### AccessString

Access permissions string used for this user account.

_Required_: Yes

_Type_: String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### AuthenticationMode

_Required_: Yes

_Type_: <a href="authenticationmode.md">AuthenticationMode</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Tags

An array of key-value pairs to apply to this user.

_Required_: No

_Type_: List of <a href="tag.md">Tag</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the UserName.

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### Status

Indicates the user status. Can be "active", "modifying" or "deleting".

#### Arn

The Amazon Resource Name (ARN) of the user account.

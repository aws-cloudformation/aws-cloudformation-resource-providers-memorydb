# AWS::MemoryDB::ParameterGroup

The AWS::MemoryDB::ParameterGroup resource creates an Amazon MemoryDB ParameterGroup.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::MemoryDB::ParameterGroup",
    "Properties" : {
        "<a href="#parametergroupname" title="ParameterGroupName">ParameterGroupName</a>" : <i>String</i>,
        "<a href="#family" title="Family">Family</a>" : <i>String</i>,
        "<a href="#description" title="Description">Description</a>" : <i>String</i>,
        "<a href="#tags" title="Tags">Tags</a>" : <i>[ <a href="tag.md">Tag</a>, ... ]</i>,
        "<a href="#parameters" title="Parameters">Parameters</a>" : <i>Map</i>,
    }
}
</pre>

### YAML

<pre>
Type: AWS::MemoryDB::ParameterGroup
Properties:
    <a href="#parametergroupname" title="ParameterGroupName">ParameterGroupName</a>: <i>String</i>
    <a href="#family" title="Family">Family</a>: <i>String</i>
    <a href="#description" title="Description">Description</a>: <i>String</i>
    <a href="#tags" title="Tags">Tags</a>: <i>
      - <a href="tag.md">Tag</a></i>
    <a href="#parameters" title="Parameters">Parameters</a>: <i>Map</i>
</pre>

## Properties

#### ParameterGroupName

The name of the parameter group.

_Required_: Yes

_Type_: String

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### Family

The name of the parameter group family that this parameter group is compatible with.

_Required_: No

_Type_: String

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### Description

A description of the parameter group.

_Required_: No

_Type_: String

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### Tags

An array of key-value pairs to apply to this parameter group.

_Required_: No

_Type_: List of <a href="tag.md">Tag</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Parameters

An map of parameter names and values for the parameter update. You must supply at least one parameter name and value; subsequent arguments are optional.

_Required_: No

_Type_: Map

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the ParameterGroupName.

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### ARN

The Amazon Resource Name (ARN) of the parameter group.

import React, { Fragment } from 'react';
import { FC, PropsWithChildren } from 'react';

export type ReactJoinProps = {
  separator?: React.ReactNode;
};

export const ReactJoin: FC<PropsWithChildren<ReactJoinProps>> = (props) => {
  const { children, separator = ', ' } = props;
  const kids = React.Children.toArray(children);
  return (
    <>
      {kids.map((child, index) => (
        <Fragment key={index}>
          {index > 0 && separator}
          {child}
        </Fragment>
      ))}
    </>
  );
};

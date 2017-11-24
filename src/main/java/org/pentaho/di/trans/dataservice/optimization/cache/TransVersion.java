package org.pentaho.di.trans.dataservice.optimization.cache;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.pentaho.di.trans.TransMeta;

public class TransVersion {
  private int sizeRowSet;


  public TransVersion( TransMeta trans ) {
    this.sizeRowSet = trans.getSizeRowset();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    final TransVersion other = (TransVersion) obj;

    if (this.sizeRowSet != other.sizeRowSet) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 31)
        .append(this.sizeRowSet)
        .toHashCode();
  }
}

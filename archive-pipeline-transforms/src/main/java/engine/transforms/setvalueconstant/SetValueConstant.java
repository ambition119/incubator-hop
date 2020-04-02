/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.pipeline.transforms.setvalueconstant;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.util.StringUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformDataInterface;
import org.apache.hop.pipeline.transform.TransformInterface;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaInterface;

import java.util.List;

/**
 * Replace Field value by a constant value.
 *
 * @author Samatar
 * @since 30-06-2008
 */

public class SetValueConstant extends BaseTransform implements TransformInterface {
  private static Class<?> PKG = SetValueConstantMeta.class; // for i18n purposes, needed by Translator!!

  private SetValueConstantMeta meta;
  private SetValueConstantData data;

  public SetValueConstant( TransformMeta transformMeta, TransformDataInterface transformDataInterface, int copyNr, PipelineMeta pipelineMeta,
                           Pipeline pipeline ) {
    super( transformMeta, transformDataInterface, copyNr, pipelineMeta, pipeline );
  }

  //CHECKSTYLE:Indentation:OFF
  public boolean processRow( TransformMetaInterface smi, TransformDataInterface sdi ) throws HopException {
    meta = (SetValueConstantMeta) smi;
    data = (SetValueConstantData) sdi;

    Object[] r = getRow(); // get row, set busy!
    if ( r == null ) { // no more input to be expected...

      setOutputDone();
      return false;
    }

    if ( first ) {
      first = false;

      // What's the format of the output row?
      data.setOutputRowMeta( getInputRowMeta().clone() );
      meta.getFields( data.getOutputRowMeta(), getTransformName(), null, null, this, metaStore );
      // Create convert meta-data objects that will contain Date & Number formatters
      // data.convertRowMeta = data.outputRowMeta.clone();

      // For String to <type> conversions, we allocate a conversion meta data row as well...
      //
      data.setConvertRowMeta( data.getOutputRowMeta().cloneToType( ValueMetaInterface.TYPE_STRING ) );

      // Consider only selected fields
      List<SetValueConstantMeta.Field> fields = meta.getFields();
      int size = fields.size();
      if ( !Utils.isEmpty( fields ) ) {
        data.setFieldnrs( new int[ size ] );
        data.setRealReplaceByValues( new String[ size ] );
        for ( int i = 0; i < size; i++ ) {
          // Check if this field was specified only one time
          final SetValueConstantMeta.Field check = fields.get( i );
          for ( SetValueConstantMeta.Field field : fields ) {
            if ( field.getFieldName() != null && field != check && field.getFieldName().equalsIgnoreCase( check.getFieldName() ) ) {
              throw new HopException( BaseMessages.getString( PKG, "SetValueConstant.Log"
                + ".FieldSpecifiedMoreThatOne", check.getFieldName() ) );
            }
          }

          data.getFieldnrs()[ i ] = data.getOutputRowMeta().indexOfValue( meta.getField( i ).getFieldName() );

          if ( data.getFieldnrs()[ i ] < 0 ) {
            logError( BaseMessages.getString( PKG, "SetValueConstant.Log.CanNotFindField", meta.getField( i ).getFieldName() ) );
            throw new HopException( BaseMessages.getString( PKG, "SetValueConstant.Log.CanNotFindField", meta
              .getField( i ).getFieldName() ) );
          }

          if ( meta.getField( i ).isEmptyString() ) {
            // Just set empty string
            data.getRealReplaceByValues()[ i ] = StringUtil.EMPTY_STRING;
          } else {
            // set specified value
            if ( meta.isUseVars() ) {
              data.getRealReplaceByValues()[ i ] = environmentSubstitute( meta.getField( i ).getReplaceValue() );
            } else {
              data.getRealReplaceByValues()[ i ] = meta.getField( i ).getReplaceValue();
            }
          }
        }
      } else {
        throw new HopException( BaseMessages.getString( PKG, "SetValueConstant.Log.SelectFieldsEmpty" ) );
      }

      data.setFieldnr( data.getFieldnrs().length );

    } // end if first

    try {
      updateField( r );
      putRow( data.getOutputRowMeta(), r ); // copy row to output rowset(s);
    } catch ( Exception e ) {
      if ( getTransformMeta().isDoingErrorHandling() ) {
        // Simply add this row to the error row
        putError( data.getOutputRowMeta(), r, 1, e.toString(), null, "SVC001" );
      } else {
        logError( BaseMessages.getString( PKG, "SetValueConstant.Log.ErrorInTransform", e.getMessage() ) );
        setErrors( 1 );
        stopAll();
        setOutputDone(); // signal end to receiver(s)
        return false;
      }
    }
    return true;
  }

  private void updateField( Object[] r ) throws Exception {
    // Loop through fields
    for ( int i = 0; i < data.getFieldnr(); i++ ) {
      // DO CONVERSION OF THE DEFAULT VALUE ...
      // Entered by user
      ValueMetaInterface targetValueMeta = data.getOutputRowMeta().getValueMeta( data.getFieldnrs()[ i ] );
      ValueMetaInterface sourceValueMeta = data.getConvertRowMeta().getValueMeta( data.getFieldnrs()[ i ] );

      if ( !Utils.isEmpty( meta.getField( i ).getReplaceMask() ) ) {
        sourceValueMeta.setConversionMask( meta.getField( i ).getReplaceMask() );
      }

      sourceValueMeta.setStorageType( ValueMetaInterface.STORAGE_TYPE_NORMAL );
      r[ data.getFieldnrs()[ i ] ] = targetValueMeta.convertData( sourceValueMeta, data.getRealReplaceByValues()[ i ] );
      targetValueMeta.setStorageType( ValueMetaInterface.STORAGE_TYPE_NORMAL );
    }
  }

  public boolean init( TransformMetaInterface smi, TransformDataInterface sdi ) {
    meta = (SetValueConstantMeta) smi;
    data = (SetValueConstantData) sdi;

    return super.init( smi, sdi );
  }

}